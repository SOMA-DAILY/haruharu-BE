package org.son.haruharu.domain.room.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.storage.RoomEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RoomTypeTest {
    @Test
    @DisplayName("기존 방 생성은 운영 기본값이며 구분 변경은 일정과 상태를 보존한다")
    void givenOperationalRoom_whenChangingType_thenPreservesRoomData() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 10, 19, 0);
        Room room = Room.createScheduled(start, start.plusHours(60), start.plusHours(63),
                start.plusHours(64), start.plusHours(72), 3);
        assertThat(room.isTest()).isFalse();
        assertThat(Room.create(LocalDate.of(2026, 10, 10), 3).isTest()).isFalse();
        Room test = room.withTestType(true);
        assertThat(test).usingRecursiveComparison().ignoringFields("isTest").isEqualTo(room);
        assertThat(test.isTest()).isTrue();
        assertThat(test.withTestType(false)).isEqualTo(room);
    }

    @Test
    @DisplayName("테스트 구분은 모든 상태 전이와 Entity 왕복에서 유지된다")
    void givenTestRoom_whenTransitioningAndPersisting_thenPreservesTestType() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 10, 19, 0);
        Room room = Room.createScheduled(start, start.plusHours(60), start.plusHours(63),
                start.plusHours(64), start.plusHours(72), 3).withTestType(true);
        assertThat(room.materialized(RoomStatus.ACTIVE).isTest()).isTrue();
        assertThat(room.materialized(RoomStatus.SELECTING).isTest()).isTrue();
        assertThat(room.materialized(RoomStatus.ENDED).isTest()).isTrue();
        assertThat(room.started(start).isTest()).isTrue();
        assertThat(room.ended(start.plusDays(3)).isTest()).isTrue();
        assertThat(room.cancelled().isTest()).isTrue();
        assertThat(RoomEntity.from(room).toDomain()).isEqualTo(room);
    }
}
