package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
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

    private Room roomWithStatus(Long id, RoomStatus status) {
        return new Room(id, status, LocalDate.of(2026, 6, 24), 3, null, null);
    }

    @Test
    @DisplayName("findCurrent는 CANCELLED 방을 현재 방에서 제외한다(#311)")
    void givenOnlyCancelledRoom_whenFindCurrent_thenEmpty() {
        RoomReader reader = new RoomReader(roomRepository);
        given(roomRepository.findByIdInAndStatusNot(List.of(10L), RoomStatus.ENDED))
                .willReturn(List.of(roomWithStatus(10L, RoomStatus.CANCELLED)));

        assertThat(reader.findCurrent(List.of(10L))).isEmpty();
    }

    @Test
    @DisplayName("findCurrent는 진행 중(ACTIVE) 방을 현재 방으로 반환한다")
    void givenActiveRoom_whenFindCurrent_thenPresent() {
        RoomReader reader = new RoomReader(roomRepository);
        given(roomRepository.findByIdInAndStatusNot(List.of(10L), RoomStatus.ENDED))
                .willReturn(List.of(roomWithStatus(10L, RoomStatus.ACTIVE)));

        assertThat(reader.findCurrent(List.of(10L))).isPresent();
    }
}
