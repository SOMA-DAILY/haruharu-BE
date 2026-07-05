package org.son.haruharu.domain.matching.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.matching.business.event.RoomMatchedEvent;
import org.son.haruharu.domain.matching.implement.FifoMatchingStrategy;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class MatchingExecutorTest {

    @Mock MatchingQueueReader matchingQueueReader;
    @Mock MatchingQueueWriter matchingQueueWriter;
    @Mock RoomWriter roomWriter;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock CoinService coinService;
    @Mock ApplicationEventPublisher eventPublisher;

    MatchingExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new MatchingExecutor(
            matchingQueueReader, matchingQueueWriter,
            new FifoMatchingStrategy(), roomWriter, roomMemberWriter, coinService, eventPublisher);
    }

    private List<MatchingQueue> queues(String gender, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new MatchingQueue(
                (long) i, (long) i, gender, null,
                MatchingQueueStatus.QUEUED, LocalDateTime.now().plusSeconds(i), null))
            .toList();
    }

    @Test
    @DisplayName("남3·여3이 대기 중이면 방 1개를 만들고 멤버 6명 저장·큐 6건을 MATCHED로 전이한다")
    void given3v3Queued_whenExecute_thenCreatesOneRoom() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 3).stream(), queues("FEMALE", 3).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(roomWriter.save(any())).willAnswer(inv -> {
            Room r = inv.getArgument(0);
            return new Room(1L, r.status(), r.scheduledDate(),
                r.totalDays(), r.startedAt(), r.endedAt());
        });

        int created = executor.execute(LocalDate.now(), LocalDateTime.now());

        assertThat(created).isEqualTo(1);
        then(roomWriter).should(times(1)).save(any());
        then(roomMemberWriter).should(times(1)).saveAll(any());
        then(matchingQueueWriter).should(times(6)).save(any());
        then(matchingQueueWriter).should(never()).expireIfQueued(any(), any());
        then(coinService).should(never()).credit(any());
    }

    @Test
    @DisplayName("남6·여6이 대기 중이면 방 2개를 만들고 큐 12건을 MATCHED로 전이한다")
    void given6v6Queued_whenExecute_thenCreatesTwoRooms() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 6).stream(), queues("FEMALE", 6).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(roomWriter.save(any())).willAnswer(inv -> {
            Room r = inv.getArgument(0);
            return new Room(1L, r.status(), r.scheduledDate(),
                r.totalDays(), r.startedAt(), r.endedAt());
        });

        int created = executor.execute(LocalDate.now(), LocalDateTime.now());

        assertThat(created).isEqualTo(2);
        then(roomWriter).should(times(2)).save(any());
        then(roomMemberWriter).should(times(2)).saveAll(any());
        then(matchingQueueWriter).should(times(12)).save(any());
    }

    @Test
    @DisplayName("성비가 안 맞으면(남2·여3) 방을 만들지 않고 잔류 큐 5건을 EXPIRED 전이 후 구름 5개씩 환불한다")
    void givenInsufficientRatio_whenExecute_thenNoRoomAndRefundsLeftovers() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 2).stream(), queues("FEMALE", 3).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(matchingQueueWriter.expireIfQueued(any(), any())).willReturn(true);

        int created = executor.execute(LocalDate.now(), LocalDateTime.now());

        assertThat(created).isZero();
        then(roomWriter).should(never()).save(any());
        then(matchingQueueWriter).should(times(5)).expireIfQueued(any(), any());

        ArgumentCaptor<CoinCreditCommand> creditCaptor = ArgumentCaptor.forClass(CoinCreditCommand.class);
        then(coinService).should(times(5)).credit(creditCaptor.capture());
        assertThat(creditCaptor.getAllValues()).allSatisfy(c -> {
            assertThat(c.amount()).isEqualTo(MatchingQueue.ENTER_COST);
            assertThat(c.reason()).isEqualTo(CoinLedgerReason.MATCH_REFUND);
        });
        // 환불 대상 userId가 잔류 큐 유저와 일치
        assertThat(creditCaptor.getAllValues()).extracting(CoinCreditCommand::userId)
            .containsExactlyInAnyOrderElementsOf(all.stream().map(MatchingQueue::userId).toList());
    }

    @Test
    @DisplayName("남4·여3이면 방 1개(6명 MATCHED) 확정 후 잔류 1건만 EXPIRED 전이·환불한다")
    void givenPartialMatch_whenExecute_thenRefundsOnlyLeftover() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 4).stream(), queues("FEMALE", 3).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(matchingQueueWriter.expireIfQueued(any(), any())).willReturn(true);
        given(roomWriter.save(any())).willAnswer(inv -> {
            Room r = inv.getArgument(0);
            return new Room(1L, r.status(), r.scheduledDate(),
                r.totalDays(), r.startedAt(), r.endedAt());
        });

        int created = executor.execute(LocalDate.now(), LocalDateTime.now());

        assertThat(created).isEqualTo(1);
        then(matchingQueueWriter).should(times(6)).save(any()); // MATCHED 6건
        then(matchingQueueWriter).should(times(1)).expireIfQueued(any(), any());
        then(coinService).should(times(1)).credit(any());
    }

    @Test
    @DisplayName("방이 확정되면 방마다 멤버 전원 userId를 담은 RoomMatchedEvent를 발행한다")
    void givenMatched_whenExecute_thenPublishesRoomMatchedEventPerRoom() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 3).stream(), queues("FEMALE", 3).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(roomWriter.save(any())).willAnswer(inv -> {
            Room r = inv.getArgument(0);
            return new Room(1L, r.status(), r.scheduledDate(),
                r.totalDays(), r.startedAt(), r.endedAt());
        });

        executor.execute(LocalDate.now(), LocalDateTime.now());

        ArgumentCaptor<RoomMatchedEvent> eventCaptor = ArgumentCaptor.forClass(RoomMatchedEvent.class);
        then(eventPublisher).should(times(1)).publishEvent(eventCaptor.capture());
        RoomMatchedEvent event = eventCaptor.getValue();
        assertThat(event.roomId()).isEqualTo(1L);
        assertThat(event.userIds())
            .containsExactlyInAnyOrderElementsOf(all.stream().map(MatchingQueue::userId).toList());
    }

    @Test
    @DisplayName("매칭이 없으면 RoomMatchedEvent를 발행하지 않는다")
    void givenNoMatch_whenExecute_thenNoEvent() {
        List<MatchingQueue> all = queues("MALE", 2);
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(matchingQueueWriter.expireIfQueued(any(), any())).willReturn(true);

        executor.execute(LocalDate.now(), LocalDateTime.now());

        then(eventPublisher).should(never()).publishEvent(any(RoomMatchedEvent.class));
    }

    @Test
    @DisplayName("잔류 큐가 배치 도중 이미 취소됐으면(전이 실패) 환불하지 않는다(이중 환불 방지)")
    void givenLeftoverAlreadyCancelled_whenExecute_thenNoRefund() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 2).stream(), queues("FEMALE", 3).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(matchingQueueWriter.expireIfQueued(any(), any())).willReturn(false);

        executor.execute(LocalDate.now(), LocalDateTime.now());

        then(coinService).should(never()).credit(any());
    }
}
