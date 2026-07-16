package org.son.haruharu.domain.matching.business;

import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinSpendCommand;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.matching.business.dto.LastApplyResultResponse;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.matching.business.exception.MatchingAlreadyQueuedException;
import org.son.haruharu.domain.matching.business.exception.MatchingClosedException;
import org.son.haruharu.domain.matching.business.exception.MatchingNotVerifiedException;
import org.son.haruharu.domain.matching.business.exception.MatchingRoomInProgressException;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.matching.storage.exception.MatchingQueueNotFoundException;
import org.son.haruharu.domain.room.business.RoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MatchingService {

    private final MatchingQueueReader matchingQueueReader;
    private final MatchingQueueWriter matchingQueueWriter;
    private final IdentityService identityService;
    private final CoinService coinService;
    private final RoomService roomService;
    private final MatchingProperties matchingProperties;
    private final Clock clock;

    public MatchingService(
        MatchingQueueReader matchingQueueReader,
        MatchingQueueWriter matchingQueueWriter,
        IdentityService identityService,
        CoinService coinService,
        RoomService roomService,
        MatchingProperties matchingProperties,
        Clock clock
    ) {
        this.matchingQueueReader = matchingQueueReader;
        this.matchingQueueWriter = matchingQueueWriter;
        this.identityService = identityService;
        this.coinService = coinService;
        this.roomService = roomService;
        this.matchingProperties = matchingProperties;
        this.clock = clock;
    }

    public QueueStatusResponse getQueueStatus(Long userId) {
        return matchingQueueReader.findQueued(userId)
            .map(QueueStatusResponse::queued)
            .orElseGet(QueueStatusResponse::none);
    }

    /**
     * 직전 신청의 불발 결과. 가장 최근 큐 행이 EXPIRED(22:00 배치 미매칭·환불)일 때만 노출한다.
     * 재신청 시 최신 QUEUED 행이 잡혀 자연히 비고, 신청 이력이 없으면 비어 있다.
     */
    public Optional<LastApplyResultResponse> getLastApplyResult(Long userId) {
        return matchingQueueReader.findLatestOutcome(userId)
            .filter(outcome -> outcome.status() == MatchingQueueStatus.EXPIRED)
            .map(outcome -> new LastApplyResultResponse(
                MatchingQueueStatus.EXPIRED.name(), outcome.resolvedAt(), matchingProperties.enterCost()));
    }

    @Transactional
    public QueueResponse joinQueue(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 발표 시간대(21:00~22:00)면 참가 차단. 22:00에 자동 재개(#229).
        if (!MatchingQueue.isApplyOpen(now.toLocalTime())) {
            throw new MatchingClosedException();
        }
        // 성별·생년월일은 본인인증(identity)에서만 온다. 미인증이면 참가 차단.
        VerifiedIdentity verified = identityService.findVerified(userId)
            .orElseThrow(MatchingNotVerifiedException::new);
        if (roomService.hasCurrentRoom(userId)) {
            throw new MatchingRoomInProgressException();
        }
        if (matchingQueueReader.isQueued(userId)) {
            throw new MatchingAlreadyQueuedException();
        }
        MatchingQueue saved = matchingQueueWriter.save(
            MatchingQueue.join(userId, verified.gender(), verified.birthday(), now));
        // 차감 실패(잔액 부족) 시 예외로 트랜잭션 전체 롤백 → 큐 등록도 함께 취소된다.
        coinService.spend(CoinSpendCommand.matchEnter(
            userId, matchingProperties.enterCost(), MatchingQueue.COIN_REF_TYPE, saved.id().toString()));
        return QueueResponse.from(saved);
    }

    @Transactional
    public void leaveQueue(Long userId) {
        MatchingQueue queued = matchingQueueReader.getQueued(userId);
        // 조건부 전이(QUEUED일 때만)로 동시 취소·배치 만료와의 경합 시 이중 환불을 차단한다.
        if (!matchingQueueWriter.cancelIfQueued(queued.id(), LocalDateTime.now(clock))) {
            throw new MatchingQueueNotFoundException();
        }
        coinService.credit(CoinCreditCommand.matchRefund(
            userId, matchingProperties.enterCost(), MatchingQueue.COIN_REF_TYPE, queued.id().toString()));
    }

    /**
     * 탈퇴 시 잔여 매칭 신청을 취소·환불한다(#255). 큐가 없으면 아무 일도 하지 않는다.
     * 정리하지 않으면 22:00 배치(MatchingExecutor)가 status=QUEUED 를 전부 대상으로 삼아
     * 탈퇴자를 방에 배정하고, 같은 방의 정상 유저가 유령 멤버와 3일을 보내게 된다.
     * leaveQueue 와 달리 큐 부재를 정상 흐름으로 처리한다 — 탈퇴자 대부분은 큐가 없다.
     */
    @Transactional
    public void cancelQueueOnWithdraw(Long userId) {
        matchingQueueReader.findQueued(userId).ifPresent(queued -> {
            if (matchingQueueWriter.cancelIfQueued(queued.id(), LocalDateTime.now(clock))) {
                coinService.credit(CoinCreditCommand.matchRefund(
                    userId, matchingProperties.enterCost(), MatchingQueue.COIN_REF_TYPE, queued.id().toString()));
            }
        });
    }
}
