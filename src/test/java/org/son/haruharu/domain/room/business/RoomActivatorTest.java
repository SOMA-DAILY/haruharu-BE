package org.son.haruharu.domain.room.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RoomActivatorTest {

    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;

    RoomActivator roomActivator;

    @BeforeEach
    void setUp() {
        roomActivator = new RoomActivator(roomReader, roomWriter);
    }

    @Test
    @DisplayName("당일 시작 예정(PENDING) 방들을 ACTIVE·currentDay=1로 전환한다")
    void givenPendingRooms_whenActivate_thenStartsAll() {
        LocalDate date = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        given(roomReader.getScheduledPending(date)).willReturn(List.of(
            new Room(UUID.randomUUID(), RoomStatus.PENDING, date, 3, 0, null, null),
            new Room(UUID.randomUUID(), RoomStatus.PENDING, date, 3, 0, null, null)
        ));

        int activated = roomActivator.activate(date, now);

        assertThat(activated).isEqualTo(2);
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .allMatch(r -> r.status() == RoomStatus.ACTIVE && r.currentDay() == 1 && r.startedAt() == now);
    }

    @Test
    @DisplayName("시작 예정 방이 없으면 아무 방도 전환하지 않는다")
    void givenNoPendingRooms_whenActivate_thenDoesNothing() {
        LocalDate date = LocalDate.now();
        given(roomReader.getScheduledPending(date)).willReturn(List.of());

        int activated = roomActivator.activate(date, LocalDateTime.now());

        assertThat(activated).isZero();
        then(roomWriter).should(never()).save(any());
    }
}
