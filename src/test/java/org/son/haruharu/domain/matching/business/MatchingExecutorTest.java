package org.son.haruharu.domain.matching.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.matching.implement.FifoMatchingStrategy;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueStatus;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;

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

    MatchingExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new MatchingExecutor(
            matchingQueueReader, matchingQueueWriter,
            new FifoMatchingStrategy(), roomWriter, roomMemberWriter);
    }

    private List<MatchingQueue> queues(String gender, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new MatchingQueue(
                UUID.randomUUID(), UUID.randomUUID(), gender, null,
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
            return new Room(UUID.randomUUID(), r.status(), r.scheduledDate(),
                r.totalDays(), r.startedAt(), r.endedAt());
        });

        int created = executor.execute(LocalDate.now(), LocalDateTime.now());

        assertThat(created).isEqualTo(1);
        then(roomWriter).should(times(1)).save(any());
        then(roomMemberWriter).should(times(1)).saveAll(any());
        then(matchingQueueWriter).should(times(6)).save(any());
    }

    @Test
    @DisplayName("남6·여6이 대기 중이면 방 2개를 만들고 큐 12건을 MATCHED로 전이한다")
    void given6v6Queued_whenExecute_thenCreatesTwoRooms() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 6).stream(), queues("FEMALE", 6).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);
        given(roomWriter.save(any())).willAnswer(inv -> {
            Room r = inv.getArgument(0);
            return new Room(UUID.randomUUID(), r.status(), r.scheduledDate(),
                r.totalDays(), r.startedAt(), r.endedAt());
        });

        int created = executor.execute(LocalDate.now(), LocalDateTime.now());

        assertThat(created).isEqualTo(2);
        then(roomWriter).should(times(2)).save(any());
        then(roomMemberWriter).should(times(2)).saveAll(any());
        then(matchingQueueWriter).should(times(12)).save(any());
    }

    @Test
    @DisplayName("성비가 안 맞으면(남2·여3) 방을 만들지 않고 큐를 이월한다")
    void givenInsufficientRatio_whenExecute_thenNoRoom() {
        List<MatchingQueue> all = Stream.concat(queues("MALE", 2).stream(), queues("FEMALE", 3).stream()).toList();
        given(matchingQueueReader.findAllQueued()).willReturn(all);

        int created = executor.execute(LocalDate.now(), LocalDateTime.now());

        assertThat(created).isZero();
        then(roomWriter).should(never()).save(any());
        then(matchingQueueWriter).should(never()).save(any());
    }
}
