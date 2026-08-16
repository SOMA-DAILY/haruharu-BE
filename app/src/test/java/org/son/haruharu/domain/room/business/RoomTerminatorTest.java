package org.son.haruharu.domain.room.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RoomTerminatorTest {

    private static final LocalDate SCHEDULED = LocalDate.of(2026, 6, 24);
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 6, 24, 9, 0);

    @Mock RoomReader roomReader;
    @Mock RoomWriter roomWriter;

    RoomTerminator roomTerminator;

    @BeforeEach
    void setUp() {
        roomTerminator = new RoomTerminator(roomReader, roomWriter);
    }

    private Room activeRoom(Long id) {
        return new Room(id, RoomStatus.ACTIVE, SCHEDULED, 3, STARTED_AT, null);
    }

    @Test
    @DisplayName("마지막 날 경과 ACTIVE 방만 ENDED로 전환하고 종료 수를 반환한다")
    void givenExpiredActiveRooms_whenTerminate_thenEndsOnlyExpired() {
        // given: room 1은 마지막 날 경과, room 2는 아직 마지막 날(Day3)
        LocalDateTime now = LocalDateTime.of(2026, 6, 27, 9, 0);
        Room expired = activeRoom(1L);
        Room ongoing = new Room(2L, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 26),
                3, LocalDateTime.of(2026, 6, 26, 9, 0), null);
        given(roomReader.getLegacyActive()).willReturn(List.of(expired, ongoing));

        // when
        int ended = roomTerminator.terminate(now);

        // then
        assertThat(ended).isEqualTo(1);
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        then(roomWriter).should(times(1)).save(captor.capture());
        Room saved = captor.getValue();
        assertThat(saved.id()).isEqualTo(1L);
        assertThat(saved.status()).isEqualTo(RoomStatus.ENDED);
        assertThat(saved.endedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("종료 대상 방이 없으면 아무 방도 전환하지 않는다")
    void givenNoExpiredRooms_whenTerminate_thenDoesNothing() {
        // given: Day3(마지막 날) 진행 중 방만 있음
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 12, 0);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom(1L)));

        // when
        int ended = roomTerminator.terminate(now);

        // then
        assertThat(ended).isZero();
        then(roomWriter).should(never()).save(any());
    }
}
