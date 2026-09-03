package org.son.haruharu.domain.room.storage;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;

import static org.assertj.core.api.Assertions.assertThat;

class RoomEntityTest {

    @Test
    @DisplayName("RoomEntity 왕복 변환은 방 스케줄을 보존한다")
    void givenRoomWithSchedule_whenEntityRoundTrips_thenScheduleIsPreserved() {
        LocalDateTime starts = LocalDateTime.of(2026, 9, 3, 19, 0);
        Room room = Room.createScheduled(starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3), 3);

        assertThat(RoomEntity.from(room).toDomain().startsAt()).isEqualTo(starts);
    }
}
