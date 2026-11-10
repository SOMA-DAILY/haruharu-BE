package org.son.haruharu.domain.replyrecommendation.business;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisSnapshotPreparer;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisSnapshotQueue;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationAccess;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationHousekeeping;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationReason;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequest;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequestStore;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationResolver;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스케줄러가 부르는 뒷정리(app 모듈). 스냅샷은 행 단위로 잡아(SKIP LOCKED) 한 서버만 만든다. 개인화 진행은 잡지 않지만
 * 유니크 키(개인 입력·작업 키)와 대기 상태 조건부 갱신으로 여러 서버가 겹쳐도 결과는 하나다.
 * 각 단위는 자기 트랜잭션(REQUIRES_NEW)이라 하나가 실패해도 나머지를 계속한다.
 */
@Service
@RequiredArgsConstructor
public class ReplyRecommendationMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(ReplyRecommendationMaintenanceService.class);
    private static final Duration PERSONAL_LOOKBACK = Duration.ofHours(1);

    private final ReplyRecommendationProperties properties;
    private final AnalysisSnapshotQueue queue;
    private final AnalysisSnapshotPreparer preparer;
    private final RecommendationRequestStore requests;
    private final RecommendationResolver resolver;
    private final RecommendationHousekeeping housekeeping;
    private final org.son.haruharu.domain.replyrecommendation.implement.RecommendationAccessJudge accessJudge;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    /** 대기 중인 분석 요청을 입력 스냅샷으로 만들고 분석 작업을 넣는다. 처리한 개수를 돌려준다. */
    public int prepareSnapshots() {
        if (!properties.enabled() || !queue.ready()) {
            return 0;
        }
        TransactionTemplate tx = newTransaction();
        int prepared = 0;
        for (int i = 0; i < properties.snapshot().batchSize(); i++) {
            Instant now = clock.instant();
            Optional<Long> claimed = tx.execute(ignored -> queue.claimNext(now));
            if (claimed == null || claimed.isEmpty()) {
                break;
            }
            Long id = claimed.get();
            try {
                tx.executeWithoutResult(ignored -> preparer.prepare(id, now));
                prepared++;
            } catch (RuntimeException failure) {
                log.atWarn().setCause(failure).addKeyValue("event", "REPLY_RECOMMENDATION_SNAPSHOT_FAILED")
                        .addKeyValue("analysisRequestId", id).log("분석 입력 스냅샷 실패, 다시 시도");
                tx.executeWithoutResult(ignored -> queue.retryLater(id, "SNAPSHOT_ERROR", now));
            }
        }
        return prepared;
    }

    /** 개인화 입력처럼 앱 서버만 할 수 있는 다음 단계를 진행한다(조회 API 는 작업을 만들지 않는다). */
    public int advancePersonalRequests() {
        if (!properties.personalizationEnabled() || !queue.ready()) {
            return 0;
        }
        TransactionTemplate tx = newTransaction();
        Instant now = clock.instant();
        List<RecommendationRequest> pending = tx.execute(ignored ->
                requests.findPendingPersonal(now.minus(PERSONAL_LOOKBACK), 50));
        int advanced = 0;
        for (RecommendationRequest request : pending == null ? List.<RecommendationRequest>of() : pending) {
            try {
                tx.executeWithoutResult(ignored -> {
                    // 그사이 방을 나갔거나 차단했으면 유료 작업을 만들지 않고 닫는다.
                    RecommendationAccess access = accessJudge.judge(request.roomId(), request.videoId(),
                            request.viewerId());
                    if (!access.allowed()) {
                        resolver.close(request, access.reason() == null
                                ? RecommendationReason.INVALIDATED : access.reason(), clock.instant());
                        return;
                    }
                    resolver.resolve(request, RecommendationResolver.Mode.BACKGROUND, clock.instant());
                });
                advanced++;
            } catch (RuntimeException failure) {
                log.atWarn().setCause(failure).addKeyValue("event", "REPLY_RECOMMENDATION_ADVANCE_FAILED")
                        .addKeyValue("requestId", request.requestId()).log("개인화 단계 진행 실패");
            }
        }
        return advanced;
    }

    /** 24시간 답장 없던 노출을 닫고, 삭제·숨김된 영상의 추천과 파생 자료를 막는다. */
    public void maintain() {
        if (!housekeeping.ready()) {
            return;
        }
        TransactionTemplate tx = newTransaction();
        Instant now = clock.instant();
        Integer closed = tx.execute(ignored -> housekeeping.closeExpiredEpisodes(now));
        tx.executeWithoutResult(ignored -> housekeeping.purgeDeletedReplies(now));
        Set<Long> unavailable = housekeeping.findUnavailableAnalyzedVideos(now);
        for (Long videoId : unavailable) {
            tx.executeWithoutResult(ignored -> housekeeping.revokeVideo(videoId, now));
        }
        if ((closed != null && closed > 0) || !unavailable.isEmpty()) {
            log.atInfo().addKeyValue("event", "REPLY_RECOMMENDATION_MAINTAINED")
                    .addKeyValue("closedEpisodes", closed).addKeyValue("revokedVideos", unavailable.size())
                    .log("답장 추천 정리");
        }
    }

    /** 보관 기간이 지난 원문·파생 자료(방 기록·캡션·분석 설명·후보·카드·답장 원문)를 지운다. */
    public void purgeExpiredSnapshots() {
        if (!housekeeping.ready()) {
            return;
        }
        Instant now = clock.instant();
        newTransaction().executeWithoutResult(ignored -> housekeeping.purgeExpired(now));
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }
}
