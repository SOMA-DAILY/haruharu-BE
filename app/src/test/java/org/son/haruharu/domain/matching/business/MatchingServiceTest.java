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
import org.son.haruharu.domain.matching.business.dto.LastApplyResultResponse;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.son.haruharu.domain.matching.business.exception.MatchingAlreadyQueuedException;
import org.son.haruharu.domain.matching.business.exception.MatchingClosedException;
import org.son.haruharu.domain.matching.business.exception.MatchingNotVerifiedException;
import org.son.haruharu.domain.matching.business.exception.MatchingRoomInProgressException;
import org.son.haruharu.domain.matching.implement.LastApplyOutcome;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.matching.storage.exception.MatchingQueueNotFoundException;
import org.son.haruharu.domain.room.business.RoomService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    MatchingService matchingService;
    final MatchingProperties matchingProperties = new MatchingProperties(5L);

    /** 지정 시각(Asia/Seoul)에 고정된 Clock으로 서비스를 만든다. 마감(21:00) 판정 테스트용. */
    private MatchingService serviceAt(int hour, int minute) {
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 7, 8, hour, minute, 0, 0, SEOUL).toInstant(), SEOUL);
        return new MatchingService(matchingQueueReader, matchingQueueWriter, identityService,
            coinService, roomService, matchingProperties, clock);
    }

    @BeforeEach
    void setUp() {
        // 기존 시나리오는 신청 개장 시간대(12:00) 기준으로 검증한다.
        matchingService = serviceAt(12, 0);
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
        assertThat(spend.amount()).isEqualTo(matchingProperties.enterCost());
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
    @DisplayName("신청 마감 시간대(21:00)에 큐 참가 시 MatchingClosedException이 발생하고 저장·차감하지 않는다")
    void givenDeadline_whenJoinQueue_thenThrowsClosed() {
        Long userId = 1L;

        assertThatThrownBy(() -> serviceAt(21, 0).joinQueue(userId))
            .isInstanceOf(MatchingClosedException.class);

        then(identityService).should(never()).findVerified(any());
        then(matchingQueueWriter).should(never()).save(any());
        then(coinService).should(never()).spend(any());
    }

    @Test
    @DisplayName("마감 직전(20:59)에는 큐 참가가 허용된다")
    void givenJustBeforeDeadline_whenJoinQueue_thenAllowed() {
        Long userId = 1L;
        givenVerified(userId);
        given(roomService.hasCurrentRoom(userId)).willReturn(false);
        given(matchingQueueReader.isQueued(userId)).willReturn(false);
        givenSaveEcho();

        QueueResponse result = serviceAt(20, 59).joinQueue(userId);

        assertThat(result.status()).isEqualTo("QUEUED");
        then(coinService).should().spend(any());
    }

    @Test
    @DisplayName("자정(00:00)에는 신청이 재개되어 큐 참가가 허용된다")
    void givenMidnightReopen_whenJoinQueue_thenAllowed() {
        Long userId = 1L;
        givenVerified(userId);
        given(roomService.hasCurrentRoom(userId)).willReturn(false);
        given(matchingQueueReader.isQueued(userId)).willReturn(false);
        givenSaveEcho();

        QueueResponse result = serviceAt(0, 0).joinQueue(userId);

        assertThat(result.status()).isEqualTo("QUEUED");
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
        assertThat(credit.amount()).isEqualTo(matchingProperties.enterCost());
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

    @Test
    @DisplayName("최근 신청이 미매칭(EXPIRED)이면 outcome·만료시각·환불액이 담긴 불발 결과를 반환한다")
    void givenLatestExpired_whenGetLastApplyResult_thenReturnsExpiredResult() {
        Long userId = 1L;
        LocalDateTime expiredAt = LocalDateTime.of(2026, 7, 14, 22, 0);
        given(matchingQueueReader.findLatestOutcome(userId))
            .willReturn(Optional.of(new LastApplyOutcome(MatchingQueueStatus.EXPIRED, expiredAt)));

        Optional<LastApplyResultResponse> result = matchingService.getLastApplyResult(userId);

        assertThat(result).isPresent();
        assertThat(result.get().outcome()).isEqualTo("EXPIRED");
        assertThat(result.get().expiredAt()).isEqualTo(expiredAt);
        assertThat(result.get().refundAmount()).isEqualTo(matchingProperties.enterCost());
    }

    @Test
    @DisplayName("최근 큐가 재신청(QUEUED)이면 불발 결과는 비어 있다(재신청 시 이전 불발 결과가 가려진다)")
    void givenLatestQueued_whenGetLastApplyResult_thenEmpty() {
        Long userId = 1L;
        given(matchingQueueReader.findLatestOutcome(userId))
            .willReturn(Optional.of(new LastApplyOutcome(MatchingQueueStatus.QUEUED, null)));

        Optional<LastApplyResultResponse> result = matchingService.getLastApplyResult(userId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("신청 이력이 없으면 불발 결과는 비어 있다")
    void givenNoHistory_whenGetLastApplyResult_thenEmpty() {
        Long userId = 1L;
        given(matchingQueueReader.findLatestOutcome(userId)).willReturn(Optional.empty());

        Optional<LastApplyResultResponse> result = matchingService.getLastApplyResult(userId);

        assertThat(result).isEmpty();
    }
}
