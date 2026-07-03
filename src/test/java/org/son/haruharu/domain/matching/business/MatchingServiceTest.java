package org.son.haruharu.domain.matching.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinSpendCommand;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.coin.implement.exception.InsufficientCoinException;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.matching.business.exception.MatchingAlreadyQueuedException;
import org.son.haruharu.domain.matching.business.exception.MatchingNotVerifiedException;
import org.son.haruharu.domain.matching.business.exception.MatchingRoomInProgressException;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.matching.storage.exception.MatchingQueueNotFoundException;
import org.son.haruharu.domain.room.business.RoomService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock MatchingQueueReader matchingQueueReader;
    @Mock MatchingQueueWriter matchingQueueWriter;
    @Mock IdentityService identityService;
    @Mock CoinService coinService;
    @Mock RoomService roomService;

    MatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(
            matchingQueueReader, matchingQueueWriter, identityService, coinService, roomService);
    }

    private void givenVerified(Long userId) {
        given(identityService.findVerified(userId))
            .willReturn(Optional.of(new VerifiedIdentity("MALE", LocalDate.of(1997, 5, 20))));
    }

    private void givenSaveEcho() {
        given(matchingQueueWriter.save(any())).willAnswer(inv -> {
            MatchingQueue q = inv.getArgument(0);
            return new MatchingQueue(1L, q.userId(), q.gender(), q.birthday(),
                q.status(), q.queuedAt(), q.matchedAt());
        });
    }

    @Test
    @DisplayName("미인증 유저가 매칭 큐 참가 시 MatchingNotVerifiedException이 발생하고 저장하지 않는다")
    void givenUnverified_whenJoinQueue_thenThrowsNotVerified() {
        Long userId = 1L;
        given(identityService.findVerified(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> matchingService.joinQueue(userId))
            .isInstanceOf(MatchingNotVerifiedException.class);

        then(matchingQueueWriter).should(never()).save(any());
        then(coinService).should(never()).spend(any());
    }

    @Test
    @DisplayName("종료되지 않은 방에 참여 중인 유저가 큐 참가 시 MatchingRoomInProgressException이 발생한다")
    void givenRoomInProgress_whenJoinQueue_thenThrowsRoomInProgress() {
        Long userId = 1L;
        givenVerified(userId);
        given(roomService.hasCurrentRoom(userId)).willReturn(true);

        assertThatThrownBy(() -> matchingService.joinQueue(userId))
            .isInstanceOf(MatchingRoomInProgressException.class);

        then(matchingQueueWriter).should(never()).save(any());
        then(coinService).should(never()).spend(any());
    }

    @Test
    @DisplayName("이미 큐에 있는 유저가 다시 참가 시 MatchingAlreadyQueuedException이 발생한다")
    void givenAlreadyQueued_whenJoinQueue_thenThrowsAlreadyQueued() {
        Long userId = 1L;
        givenVerified(userId);
        given(roomService.hasCurrentRoom(userId)).willReturn(false);
        given(matchingQueueReader.isQueued(userId)).willReturn(true);

        assertThatThrownBy(() -> matchingService.joinQueue(userId))
            .isInstanceOf(MatchingAlreadyQueuedException.class);

        then(matchingQueueWriter).should(never()).save(any());
        then(coinService).should(never()).spend(any());
    }

    @Test
    @DisplayName("인증된 미참가 유저가 큐 참가 시 QUEUED 저장 후 구름 5개를 MATCH_ENTER로 차감한다")
    void givenVerifiedNotQueued_whenJoinQueue_thenSavesQueuedAndSpendsCoins() {
        Long userId = 1L;
        givenVerified(userId);
        given(roomService.hasCurrentRoom(userId)).willReturn(false);
        given(matchingQueueReader.isQueued(userId)).willReturn(false);
        givenSaveEcho();

        QueueResponse result = matchingService.joinQueue(userId);

        assertThat(result.gender()).isEqualTo("MALE");
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.queueId()).isNotNull();

        ArgumentCaptor<CoinSpendCommand> captor = ArgumentCaptor.forClass(CoinSpendCommand.class);
        then(coinService).should().spend(captor.capture());
        CoinSpendCommand spend = captor.getValue();
        assertThat(spend.userId()).isEqualTo(userId);
        assertThat(spend.amount()).isEqualTo(MatchingQueue.ENTER_COST);
        assertThat(spend.reason()).isEqualTo(CoinLedgerReason.MATCH_ENTER);
        assertThat(spend.refType()).isEqualTo("MATCHING_QUEUE");
        assertThat(spend.refId()).isEqualTo(result.queueId().toString());
    }

    @Test
    @DisplayName("구름 잔액이 부족한 유저가 큐 참가 시 InsufficientCoinException이 전파된다(트랜잭션 롤백으로 큐 미등록)")
    void givenInsufficientCoin_whenJoinQueue_thenThrowsInsufficient() {
        Long userId = 1L;
        givenVerified(userId);
        given(roomService.hasCurrentRoom(userId)).willReturn(false);
        given(matchingQueueReader.isQueued(userId)).willReturn(false);
        givenSaveEcho();
        willThrow(new InsufficientCoinException()).given(coinService).spend(any());

        assertThatThrownBy(() -> matchingService.joinQueue(userId))
            .isInstanceOf(InsufficientCoinException.class);
    }

    @Test
    @DisplayName("큐에 있는 유저가 취소 시 CANCELLED 전이 후 구름 5개를 환불 적립한다")
    void givenQueued_whenLeaveQueue_thenTransitionsCancelledAndRefunds() {
        Long userId = 1L;
        Long queueId = 1L;
        MatchingQueue queued = new MatchingQueue(
            queueId, userId, "MALE", null, MatchingQueueStatus.QUEUED, LocalDateTime.now(), null);
        given(matchingQueueReader.getQueued(userId)).willReturn(queued);
        given(matchingQueueWriter.cancelIfQueued(any(), any())).willReturn(true);

        matchingService.leaveQueue(userId);

        then(matchingQueueWriter).should().cancelIfQueued(any(Long.class), any(LocalDateTime.class));

        ArgumentCaptor<CoinCreditCommand> creditCaptor = ArgumentCaptor.forClass(CoinCreditCommand.class);
        then(coinService).should().credit(creditCaptor.capture());
        CoinCreditCommand credit = creditCaptor.getValue();
        assertThat(credit.userId()).isEqualTo(userId);
        assertThat(credit.amount()).isEqualTo(MatchingQueue.ENTER_COST);
        assertThat(credit.bucket()).isEqualTo(CoinBucket.REWARD);
        assertThat(credit.reason()).isEqualTo(CoinLedgerReason.MATCH_REFUND);
        assertThat(credit.refType()).isEqualTo("MATCHING_QUEUE");
        assertThat(credit.refId()).isEqualTo(queueId.toString());
    }

    @Test
    @DisplayName("동시 취소로 CANCELLED 전이에 실패하면 예외가 발생하고 환불하지 않는다(이중 환불 방지)")
    void givenConcurrentCancel_whenLeaveQueue_thenThrowsAndNoRefund() {
        Long userId = 1L;
        MatchingQueue queued = new MatchingQueue(
            1L, userId, "MALE", null, MatchingQueueStatus.QUEUED, LocalDateTime.now(), null);
        given(matchingQueueReader.getQueued(userId)).willReturn(queued);
        given(matchingQueueWriter.cancelIfQueued(any(), any())).willReturn(false);

        assertThatThrownBy(() -> matchingService.leaveQueue(userId))
            .isInstanceOf(MatchingQueueNotFoundException.class);

        then(coinService).should(never()).credit(any());
    }

    @Test
    @DisplayName("큐에 없는 유저가 취소 시 MatchingQueueNotFoundException이 발생하고 환불하지 않는다")
    void givenNotQueued_whenLeaveQueue_thenThrows() {
        Long userId = 1L;
        given(matchingQueueReader.getQueued(userId)).willThrow(new MatchingQueueNotFoundException());

        assertThatThrownBy(() -> matchingService.leaveQueue(userId))
            .isInstanceOf(MatchingQueueNotFoundException.class);

        then(matchingQueueWriter).should(never()).cancelIfQueued(any(), any());
        then(coinService).should(never()).credit(any());
    }

    @Test
    @DisplayName("큐에 참가 중인 유저가 상태 조회 시 QUEUED와 queuedAt을 반환한다")
    void givenQueued_whenGetQueueStatus_thenReturnsQueued() {
        Long userId = 1L;
        LocalDateTime queuedAt = LocalDateTime.of(2026, 7, 3, 12, 0);
        MatchingQueue queued = new MatchingQueue(
            1L, userId, "MALE", null, MatchingQueueStatus.QUEUED, queuedAt, null);
        given(matchingQueueReader.findQueued(userId)).willReturn(Optional.of(queued));

        QueueStatusResponse result = matchingService.getQueueStatus(userId);

        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.queuedAt()).isEqualTo(queuedAt);
    }

    @Test
    @DisplayName("큐에 없는 유저가 상태 조회 시 NONE과 null queuedAt을 반환한다")
    void givenNotQueued_whenGetQueueStatus_thenReturnsNone() {
        Long userId = 1L;
        given(matchingQueueReader.findQueued(userId)).willReturn(Optional.empty());

        QueueStatusResponse result = matchingService.getQueueStatus(userId);

        assertThat(result.status()).isEqualTo("NONE");
        assertThat(result.queuedAt()).isNull();
    }
}
