package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RoomReaderTest {

    @Mock RoomRepository roomRepository;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 26, 12, 0);

    private Room roomWithStatus(Long id, RoomStatus status) {
        return new Room(id, status, LocalDate.of(2026, 6, 24), 3, null, null);
    }

    @Test
    @DisplayName("findCurrent는 CANCELLED 방을 현재 방에서 제외한다(#311)")
    void givenOnlyCancelledRoom_whenFindCurrent_thenEmpty() {
        RoomReader reader = new RoomReader(roomRepository);
        given(roomRepository.findByIdInAndStatusNot(List.of(10L), RoomStatus.ENDED))
                .willReturn(List.of(roomWithStatus(10L, RoomStatus.CANCELLED)));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isEmpty();
    }

    @Test
    @DisplayName("findCurrent는 진행 중(ACTIVE) 방을 현재 방으로 반환한다")
    void givenActiveRoom_whenFindCurrent_thenPresent() {
        RoomReader reader = new RoomReader(roomRepository);
        given(roomRepository.findByIdInAndStatusNot(List.of(10L), RoomStatus.ENDED))
                .willReturn(List.of(roomWithStatus(10L, RoomStatus.ACTIVE)));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isPresent();
    }

    @Test
    @DisplayName("findCurrent는 ends_at이 지난 방을 저장 status가 ACTIVE여도 제외한다 (#320 배치 지연 구간)")
    void givenStaleEndedRoom_whenFindCurrent_thenEmpty() {
        RoomReader reader = new RoomReader(roomRepository);
        LocalDateTime starts = NOW.minusDays(5);
        Room staleEnded = new Room(10L, RoomStatus.ACTIVE, starts.toLocalDate(), 3, starts, null, false, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3)); // endsAt < NOW
        given(roomRepository.findByIdInAndStatusNot(List.of(10L), RoomStatus.ENDED))
                .willReturn(List.of(staleEnded));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isEmpty();
    }

    @Test
    @DisplayName("findCurrent는 아직 시작 전(PENDING)인 새 모델 방은 현재 방으로 반환한다")
    void givenScheduledPendingRoom_whenFindCurrent_thenPresent() {
        RoomReader reader = new RoomReader(roomRepository);
        LocalDateTime starts = NOW.plusDays(1);
        Room pending = new Room(10L, RoomStatus.PENDING, starts.toLocalDate(), 3, null, null, false, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3));
        given(roomRepository.findByIdInAndStatusNot(List.of(10L), RoomStatus.ENDED))
                .willReturn(List.of(pending));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isPresent();
    }
}
