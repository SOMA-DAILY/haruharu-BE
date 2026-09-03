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
import static org.mockito.BDDMockito.then;

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
        given(roomRepository.findByIdIn(List.of(10L)))
                .willReturn(List.of(roomWithStatus(10L, RoomStatus.CANCELLED)));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isEmpty();
    }

    @Test
    @DisplayName("findCurrent는 진행 중(ACTIVE) 방을 현재 방으로 반환한다")
    void givenActiveRoom_whenFindCurrent_thenPresent() {
        RoomReader reader = new RoomReader(roomRepository);
        given(roomRepository.findByIdIn(List.of(10L)))
                .willReturn(List.of(roomWithStatus(10L, RoomStatus.ACTIVE)));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isPresent();
    }

    @Test
    @DisplayName("findCurrent는 ends_at이 지난 방을 저장 status가 ACTIVE여도 제외한다 (#320 배치 지연 구간)")
    void givenStaleEndedRoom_whenFindCurrent_thenEmpty() {
        RoomReader reader = new RoomReader(roomRepository);
        LocalDateTime starts = NOW.minusDays(5);
        Room staleEnded = new Room(10L, RoomStatus.ACTIVE, starts.toLocalDate(), 3, starts, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3)); // endsAt < NOW
        given(roomRepository.findByIdIn(List.of(10L)))
                .willReturn(List.of(staleEnded));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isEmpty();
    }

    @Test
    @DisplayName("findCurrent는 아직 시작 전(PENDING)인 새 모델 방은 현재 방으로 반환한다")
    void givenScheduledPendingRoom_whenFindCurrent_thenPresent() {
        RoomReader reader = new RoomReader(roomRepository);
        LocalDateTime starts = NOW.plusDays(1);
        Room pending = new Room(10L, RoomStatus.PENDING, starts.toLocalDate(), 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(3),
                starts.plusDays(2).plusHours(4), starts.plusDays(3));
        given(roomRepository.findByIdIn(List.of(10L)))
                .willReturn(List.of(pending));

        assertThat(reader.findCurrent(List.of(10L), NOW)).isPresent();
    }

    @Test
    @DisplayName("findCurrentAll은 동적 종료·취소 방을 제외하고 ACTIVE, SELECTING, PENDING 순으로 정렬한다")
    void givenMixedCandidateRooms_whenFindCurrentAll_thenFiltersFinalsAndUsesStableLegacyOrder() {
        RoomReader reader = new RoomReader(roomRepository);
        LocalDateTime starts = NOW.minusDays(1);
        Room staleEnded = new Room(1L, RoomStatus.ACTIVE, starts.toLocalDate(), 3, starts, null,
                starts, starts.plusHours(2), starts.plusHours(3), starts.plusHours(4), NOW);
        Room cancelled = roomWithStatus(2L, RoomStatus.CANCELLED);
        Room pending = new Room(3L, RoomStatus.PENDING, NOW.toLocalDate(), 3, null, null,
                NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4), NOW.plusHours(5));
        Room selecting = new Room(4L, RoomStatus.ACTIVE, starts.toLocalDate(), 3, starts, null,
                starts, starts.plusHours(1), starts.plusHours(2), starts.plusHours(3), starts.plusDays(2));
        Room active = new Room(5L, RoomStatus.PENDING, starts.toLocalDate(), 3, starts, null,
                starts, NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusDays(1));
        given(roomRepository.findByIdIn(List.of(1L, 2L, 3L, 4L, 5L)))
                .willReturn(List.of(pending, staleEnded, selecting, cancelled, active));

        assertThat(reader.findCurrentAll(List.of(1L, 2L, 3L, 4L, 5L), NOW))
                .extracting(Room::id)
                .containsExactly(5L, 4L, 3L);
        assertThat(reader.findCurrent(List.of(1L, 2L, 3L, 4L, 5L), NOW))
                .map(Room::id)
                .contains(5L);
    }

    @Test
    @DisplayName("findCurrentAll은 같은 동적 상태의 방을 room_id 오름차순으로 정렬한다")
    void givenSameDerivedStatus_whenFindCurrentAll_thenOrdersByRoomIdAscending() {
        RoomReader reader = new RoomReader(roomRepository);
        Room activeThree = roomWithStatus(3L, RoomStatus.ACTIVE);
        Room activeOne = roomWithStatus(1L, RoomStatus.ACTIVE);
        Room activeTwo = roomWithStatus(2L, RoomStatus.ACTIVE);
        given(roomRepository.findByIdIn(List.of(3L, 1L, 2L)))
                .willReturn(List.of(activeThree, activeOne, activeTwo));

        assertThat(reader.findCurrentAll(List.of(3L, 1L, 2L), NOW))
                .extracting(Room::id)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("findCurrentAll은 저장 status가 ENDED여도 동적으로 ACTIVE인 스케줄 방을 포함한다")
    void givenStaleStoredEndedButDynamicallyActiveRoom_whenFindCurrentAll_thenIncludesRoom() {
        RoomReader reader = new RoomReader(roomRepository);
        LocalDateTime starts = NOW.minusHours(1);
        Room staleStoredEnded = new Room(8L, RoomStatus.ENDED, starts.toLocalDate(), 3, starts, null,
                starts, NOW.plusHours(1), NOW.plusHours(2), NOW.plusHours(3), NOW.plusHours(4));
        given(roomRepository.findByIdIn(List.of(8L))).willReturn(List.of(staleStoredEnded));

        assertThat(reader.findCurrentAll(List.of(8L), NOW)).extracting(Room::id).containsExactly(8L);
    }

    @Test
    @DisplayName("findCurrentAll은 빈 후보면 저장소를 조회하지 않는다")
    void givenEmptyCandidateIds_whenFindCurrentAll_thenReturnsEmptyWithoutRepositoryCall() {
        RoomReader reader = new RoomReader(roomRepository);

        assertThat(reader.findCurrentAll(List.of(), NOW)).isEmpty();

        then(roomRepository).shouldHaveNoInteractions();
    }
}
