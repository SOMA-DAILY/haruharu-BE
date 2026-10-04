package org.son.haruharu.domain.mission.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.implement.exception.InvalidOperatorMissionScheduleException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorMissionScheduleTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 4, 12, 0);
    private final LocalDateTime selection = start.plusDays(2);
    private final Room room = Room.createScheduled(start, selection, selection.plusHours(1),
            selection.plusHours(2), selection.plusDays(1), 3);

    @Test @DisplayName("방 시작 전에도 시작 시각에 발송되는 예약을 만들 수 있다")
    void givenPendingRoom_whenScheduleAtStart_thenAccepted() {
        assertThatCode(() -> OperatorMissionSchedule.validate(room, start, start.minusHours(1)))
                .doesNotThrowAnyException();
    }

    @Test @DisplayName("예약은 현재보다 미래여야 한다")
    void givenPastOrNow_whenSchedule_thenRejected() {
        assertThatThrownBy(() -> OperatorMissionSchedule.validate(room, start, start))
                .isInstanceOf(InvalidOperatorMissionScheduleException.class);
        assertThatThrownBy(() -> OperatorMissionSchedule.validate(room, start, start.plusMinutes(1)))
                .isInstanceOf(InvalidOperatorMissionScheduleException.class);
    }

    @Test @DisplayName("방 시작 이전이나 최종선택 시작 시각부터는 예약할 수 없다")
    void givenOutsideRecordingWindow_whenSchedule_thenRejected() {
        assertThatThrownBy(() -> OperatorMissionSchedule.validate(room, start.minusSeconds(1), start.minusHours(1)))
                .isInstanceOf(InvalidOperatorMissionScheduleException.class);
        assertThatThrownBy(() -> OperatorMissionSchedule.validate(room, selection, start.minusHours(1)))
                .isInstanceOf(InvalidOperatorMissionScheduleException.class);
    }

    @Test @DisplayName("즉시 발송은 저장 status보다 실제 촬영 가능 시각으로 판정한다")
    void givenPendingStoredStatus_whenActuallyActive_thenImmediateAccepted() {
        assertThatCode(() -> OperatorMissionSchedule.validate(room, null, start)).doesNotThrowAnyException();
    }

    @Test @DisplayName("최종선택 시작 시각에는 즉시 발송도 거절한다")
    void givenSelectionWindow_whenImmediate_thenRejected() {
        assertThatThrownBy(() -> OperatorMissionSchedule.validate(room, null, selection))
                .isInstanceOf(InvalidOperatorMissionScheduleException.class);
    }

    @Test @DisplayName("취소된 방에는 미래 시각이어도 예약하지 않는다")
    void givenCancelledRoom_whenSchedule_thenRejected() {
        assertThatThrownBy(() -> OperatorMissionSchedule.validate(room.cancelled(), start.plusHours(1), start.minusHours(1)))
                .isInstanceOf(InvalidOperatorMissionScheduleException.class);
    }
}
