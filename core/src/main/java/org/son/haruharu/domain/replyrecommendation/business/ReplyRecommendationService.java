package org.son.haruharu.domain.replyrecommendation.business;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.replyrecommendation.business.dto.RecommendationResponse;
import org.son.haruharu.domain.replyrecommendation.implement.ActiveVersion;
import org.son.haruharu.domain.replyrecommendation.implement.ActiveVersionReader;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisOrigin;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisRequest;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisRequestRegistrar;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisTarget;
import org.son.haruharu.domain.replyrecommendation.implement.EnsureRateLimiter;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationAccess;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationAccessJudge;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationCard;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationGuard;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationReason;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequest;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequestStore;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationResolver;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationSet;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationProperties;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영상 답장 추천 요청(POST)·조회(GET). 시청자 권한을 매번 다시 확인하고, 유효한 결과를 돌려주거나
 * 같은 키의 작업을 한 번만 만든다. 업로드·채팅은 이 흐름을 기다리지 않는다.
 * 조회도 쓰기 트랜잭션이다: 작업기 결과로 요청 상태를 확정하거나 무효가 된 추천을 막는다(새 작업은 만들지 않는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplyRecommendationService {

    private final ReplyRecommendationProperties properties;
    private final RecommendationAccessJudge accessJudge;
    private final ActiveVersionReader versions;
    private final AnalysisRequestRegistrar registrar;
    private final RecommendationRequestStore requests;
    private final RecommendationGuard guard;
    private final RecommendationResolver resolver;
    private final EnsureRateLimiter rateLimiter;
    private final Clock clock;

    /** 유효한 캐시를 돌려주거나 멱등 작업을 만든다. 같은 키로 여러 번 불러도 요청·결과는 하나다. */
    @Transactional
    public RecommendationResponse ensure(Long roomId, Long videoId, Long viewerId) {
        if (!properties.enabledFor(roomId)) {
            return RecommendationResponse.unavailable(RecommendationReason.FEATURE_DISABLED);
        }
        RecommendationAccess access = judge(roomId, videoId, viewerId);
        if (!access.allowed()) {
            return RecommendationResponse.unavailable(access.reason());
        }
        Optional<ActiveVersion> version = versions.findActive();
        if (version.isEmpty()) {
            return RecommendationResponse.unavailable(RecommendationReason.NOT_CONFIGURED);
        }
        Instant now = clock.instant();
        AnalysisTarget target = registrar.targetFor(access.post(), access.owner(), access.scope(), version.get());
        String personalKey = personalKey();

        Optional<RecommendationRequest> current = registrar.findActive(target)
                .flatMap(analysis -> requests.findActive(viewerId, analysis.id(), version.get().versionId(), personalKey))
                .filter(request -> guard.validateOrRevoke(request, now));
        RecommendationRequest request = current.orElseGet(() -> {
            // 근거 삭제로 분석이 막혔을 수 있으니 다시 찾는다. 없으면 새 AI 작업이라 한도를 센다.
            AnalysisRequest analysis = registrar.findActive(target).orElseGet(() -> {
                rateLimiter.acquire(viewerId, now);
                return registrar.create(target, AnalysisOrigin.ENSURE, now);
            });
            return requests.createPending(viewerId, videoId, roomId, analysis.id(), version.get().versionId(),
                    properties.personalizationEnabled(), personalKey, now);
        });
        return response(resolver.resolve(request, RecommendationResolver.Mode.ENSURE, now));
    }

    /** 조회만 한다. 아직 요청이 없으면 NOT_REQUESTED, 요청 뒤 영상·캡션·버전이 바뀌었으면 INVALIDATED. */
    @Transactional
    public RecommendationResponse get(Long roomId, Long videoId, Long viewerId) {
        if (!properties.enabledFor(roomId)) {
            return RecommendationResponse.unavailable(RecommendationReason.FEATURE_DISABLED);
        }
        RecommendationAccess access = judge(roomId, videoId, viewerId);
        if (!access.allowed()) {
            return RecommendationResponse.unavailable(access.reason());
        }
        Optional<ActiveVersion> version = versions.findActive();
        if (version.isEmpty()) {
            return RecommendationResponse.unavailable(RecommendationReason.NOT_CONFIGURED);
        }
        Optional<RecommendationRequest> latest = requests.findLatest(viewerId, videoId);
        if (latest.isEmpty()) {
            return RecommendationResponse.unavailable(RecommendationReason.NOT_REQUESTED);
        }
        Instant now = clock.instant();
        RecommendationRequest request = latest.get();
        AnalysisTarget target = registrar.targetFor(access.post(), access.owner(), access.scope(), version.get());
        boolean sameKey = request.versionId().equals(version.get().versionId())
                && registrar.find(request.analysisRequestId()).map(analysis -> analysis.matches(target)).orElse(false);
        if (!sameKey || !guard.validateOrRevoke(request, now)) {
            return RecommendationResponse.unavailable(request.requestId(), RecommendationReason.INVALIDATED);
        }
        return response(resolver.resolve(request, RecommendationResolver.Mode.READ, now));
    }

    /** 단체 채팅 멤버가 아니면 403, 방이 없으면 404(기존 영상 답장과 같은 정책). */
    private RecommendationAccess judge(Long roomId, Long videoId, Long viewerId) {
        RecommendationAccess access = accessJudge.judge(roomId, videoId, viewerId);
        if (access.denial() == RecommendationAccess.Denial.NOT_MEMBER) {
            throw new ChatNotMemberException();
        }
        if (access.denial() == RecommendationAccess.Denial.ROOM_NOT_FOUND) {
            throw new RoomNotFoundException();
        }
        return access;
    }

    private RecommendationResponse response(RecommendationRequest request) {
        return switch (request.status()) {
            case PENDING -> RecommendationResponse.pending(request.requestId(), properties.pendingRetryAfterMs());
            case UNAVAILABLE -> RecommendationResponse.unavailable(request.requestId(),
                    request.reason() == null ? RecommendationReason.INVALIDATED : request.reason());
            case READY, EMPTY -> {
                List<RecommendationCard> cards = requests.findSet(request.setId())
                        .map(RecommendationSet::cards)
                        .orElse(List.of());
                yield RecommendationResponse.resolved(request.status(), request.requestId(), request.setId(),
                        request.versionId(), cards);
            }
        };
    }

    private String personalKey() {
        return properties.personalizationEnabled()
                ? RecommendationResolver.PERSONAL_KEY_V1 : RecommendationRequest.NO_PERSONAL_HASH;
    }
}
