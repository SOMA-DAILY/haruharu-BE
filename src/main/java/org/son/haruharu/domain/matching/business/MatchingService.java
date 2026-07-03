package org.son.haruharu.domain.matching.business;

import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinSpendCommand;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.matching.business.exception.MatchingAlreadyQueuedException;
import org.son.haruharu.domain.matching.business.exception.MatchingNotVerifiedException;
import org.son.haruharu.domain.matching.business.exception.MatchingRoomInProgressException;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.matching.storage.exception.MatchingQueueNotFoundException;
import org.son.haruharu.domain.room.business.RoomService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class MatchingService {

    private final MatchingQueueReader matchingQueueReader;
    private final MatchingQueueWriter matchingQueueWriter;
    private final IdentityService identityService;
    private final CoinService coinService;
    private final RoomService roomService;

    public MatchingService(
        MatchingQueueReader matchingQueueReader,
        MatchingQueueWriter matchingQueueWriter,
        IdentityService identityService,
        CoinService coinService,
        RoomService roomService
    ) {
        this.matchingQueueReader = matchingQueueReader;
        this.matchingQueueWriter = matchingQueueWriter;
        this.identityService = identityService;
        this.coinService = coinService;
        this.roomService = roomService;
    }

    public QueueStatusResponse getQueueStatus(Long userId) {
        return matchingQueueReader.findQueued(userId)
            .map(QueueStatusResponse::queued)
            .orElseGet(QueueStatusResponse::none);
    }

    @Transactional
    public QueueResponse joinQueue(Long userId) {
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
            MatchingQueue.join(userId, verified.gender(), verified.birthday(), LocalDateTime.now()));
        // 차감 실패(잔액 부족) 시 예외로 트랜잭션 전체 롤백 → 큐 등록도 함께 취소된다.
        coinService.spend(CoinSpendCommand.matchEnter(
            userId, MatchingQueue.ENTER_COST, MatchingQueue.COIN_REF_TYPE, saved.id().toString()));
        return QueueResponse.from(saved);
    }

    @Transactional
    public void leaveQueue(Long userId) {
        MatchingQueue queued = matchingQueueReader.getQueued(userId);
        // 조건부 전이(QUEUED일 때만)로 동시 취소·배치 만료와의 경합 시 이중 환불을 차단한다.
        if (!matchingQueueWriter.cancelIfQueued(queued.id(), LocalDateTime.now())) {
            throw new MatchingQueueNotFoundException();
        }
        coinService.credit(CoinCreditCommand.matchRefund(
            userId, MatchingQueue.ENTER_COST, MatchingQueue.COIN_REF_TYPE, queued.id().toString()));
    }
}
