package org.son.haruharu.domain.mission.implement;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.implement.exception.InvalidRoomMissionSlotScheduleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomMissionSlotScheduleTest {

    private static final Long ROOM_ID = 10L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 9, 1, 8, 0);
    private static final LocalDateTime SELECTION_OPENS_AT = LocalDateTime.of(2026, 9, 3, 23, 0);

    @Test
    @DisplayName("Day별 오전·오후 슬롯이 하나씩 있고 시간이 유효하면 순서대로 반환한다")
    void givenCompleteSlots_whenValidate_thenReturnsOrderedSchedule() {
        List<RoomMissionSlot> result = RoomMissionSlotSchedule.validate(
                slots(), 3, STARTS_AT, SELECTION_OPENS_AT);

        assertThat(result).extracting(RoomMissionSlot::dayNumber)
                .containsExactly(1, 1, 2, 2, 3, 3);
        assertThat(result).extracting(RoomMissionSlot::period)
                .containsExactly(MissionPeriod.MORNING, MissionPeriod.AFTERNOON,
                        MissionPeriod.MORNING, MissionPeriod.AFTERNOON,
                        MissionPeriod.MORNING, MissionPeriod.AFTERNOON);
    }

    @Test
    @DisplayName("슬롯 개수가 totalDays 곱하기 2보다 적으면 거부한다")
    void givenMissingSlot_whenValidate_thenThrows() {
        assertThatThrownBy(() -> RoomMissionSlotSchedule.validate(
                slots().subList(0, 5), 3, STARTS_AT, SELECTION_OPENS_AT))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);
    }

    @Test
    @DisplayName("같은 Day와 period가 중복되면 거부한다")
    void givenDuplicateDayAndPeriod_whenValidate_thenThrows() {
        List<RoomMissionSlot> duplicate = List.of(
                slot(1, MissionPeriod.MORNING, 9, 14),
                slot(1, MissionPeriod.MORNING, 15, 16),
                slot(2, MissionPeriod.MORNING, 33, 38),
                slot(2, MissionPeriod.AFTERNOON, 41, 46),
                slot(3, MissionPeriod.MORNING, 57, 62),
                slot(3, MissionPeriod.AFTERNOON, 65, 70));

        assertThatThrownBy(() -> RoomMissionSlotSchedule.validate(
                duplicate, 3, STARTS_AT, SELECTION_OPENS_AT))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);
    }

    @Test
    @DisplayName("두 슬롯의 시간이 겹치면 거부한다")
    void givenOverlappingSlots_whenValidate_thenThrows() {
        List<RoomMissionSlot> overlap = List.of(
                slot(1, MissionPeriod.MORNING, 1, 7),
                slot(1, MissionPeriod.AFTERNOON, 6, 10),
                slot(2, MissionPeriod.MORNING, 25, 30),
                slot(2, MissionPeriod.AFTERNOON, 33, 38),
                slot(3, MissionPeriod.MORNING, 49, 54),
                slot(3, MissionPeriod.AFTERNOON, 57, 62));

        assertThatThrownBy(() -> RoomMissionSlotSchedule.validate(
                overlap, 3, STARTS_AT, SELECTION_OPENS_AT))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);
    }

    @Test
    @DisplayName("슬롯이 방 시작 전 열리거나 최종선택 오픈 후 닫히면 거부한다")
    void givenSlotOutsideRoomWindow_whenValidate_thenThrows() {
        List<RoomMissionSlot> beforeStart = List.of(
                slot(1, MissionPeriod.MORNING, -1, 1),
                slot(1, MissionPeriod.AFTERNOON, 2, 3),
                slot(2, MissionPeriod.MORNING, 25, 30),
                slot(2, MissionPeriod.AFTERNOON, 33, 38),
                slot(3, MissionPeriod.MORNING, 49, 54),
                slot(3, MissionPeriod.AFTERNOON, 57, 62));

        assertThatThrownBy(() -> RoomMissionSlotSchedule.validate(
                beforeStart, 3, STARTS_AT, SELECTION_OPENS_AT))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);

        List<RoomMissionSlot> afterSelection = List.of(
                slot(1, MissionPeriod.MORNING, 1, 6),
                slot(1, MissionPeriod.AFTERNOON, 9, 14),
                slot(2, MissionPeriod.MORNING, 25, 30),
                slot(2, MissionPeriod.AFTERNOON, 33, 38),
                slot(3, MissionPeriod.MORNING, 49, 54),
                slot(3, MissionPeriod.AFTERNOON, 62, 64));

        assertThatThrownBy(() -> RoomMissionSlotSchedule.validate(
                afterSelection, 3, STARTS_AT, STARTS_AT.plusHours(63)))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);
    }

    @Test
    @DisplayName("Day와 period 순서가 실제 시간 순서와 다르면 거부한다")
    void givenPeriodOrderMismatch_whenValidate_thenThrows() {
        List<RoomMissionSlot> reversed = List.of(
                slot(1, MissionPeriod.MORNING, 9, 14),
                slot(1, MissionPeriod.AFTERNOON, 1, 6),
                slot(2, MissionPeriod.MORNING, 25, 30),
                slot(2, MissionPeriod.AFTERNOON, 33, 38),
                slot(3, MissionPeriod.MORNING, 49, 54),
                slot(3, MissionPeriod.AFTERNOON, 57, 62));

        assertThatThrownBy(() -> RoomMissionSlotSchedule.validate(
                reversed, 3, STARTS_AT, SELECTION_OPENS_AT))
                .isInstanceOf(InvalidRoomMissionSlotScheduleException.class);
    }

    @Test
    @DisplayName("앞 슬롯 종료와 다음 슬롯 시작이 같으면 허용한다")
    void givenTouchingSlots_whenValidate_thenAccepts() {
        List<RoomMissionSlot> touching = List.of(
                slot(1, MissionPeriod.MORNING, 1, 6),
                slot(1, MissionPeriod.AFTERNOON, 6, 10),
                slot(2, MissionPeriod.MORNING, 25, 30),
                slot(2, MissionPeriod.AFTERNOON, 33, 38),
                slot(3, MissionPeriod.MORNING, 49, 54),
                slot(3, MissionPeriod.AFTERNOON, 57, 62));

        assertThat(RoomMissionSlotSchedule.validate(
                touching, 3, STARTS_AT, SELECTION_OPENS_AT)).hasSize(6);
    }

    private List<RoomMissionSlot> slots() {
        return List.of(
                slot(1, MissionPeriod.MORNING, 1, 6),
                slot(1, MissionPeriod.AFTERNOON, 9, 14),
                slot(2, MissionPeriod.MORNING, 25, 30),
                slot(2, MissionPeriod.AFTERNOON, 33, 38),
                slot(3, MissionPeriod.MORNING, 49, 54),
                slot(3, MissionPeriod.AFTERNOON, 57, 62));
    }

    private RoomMissionSlot slot(int dayNumber, MissionPeriod period, int opensOffset, int closesOffset) {
        return RoomMissionSlot.of(ROOM_ID, dayNumber, period,
                STARTS_AT.plusHours(opensOffset), STARTS_AT.plusHours(closesOffset));
    }
}
