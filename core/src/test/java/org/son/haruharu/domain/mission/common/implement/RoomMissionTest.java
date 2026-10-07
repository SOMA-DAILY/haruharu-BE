package org.son.haruharu.domain.mission.common.implement;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.exception.BadRequestException;
import org.son.haruharu.domain.room.implement.Room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomMissionTest {
    private final LocalDateTime startsAt = LocalDateTime.of(2026, 10, 7, 10, 0);
    private final Room room = Room.createScheduled(startsAt, startsAt.plusHours(60), startsAt.plusHours(62),
            startsAt.plusHours(63), startsAt.plusHours(72), 3);

    @Test
    @DisplayName("채팅방이나 발송 상태 없이 미션의 일정만으로 진행 상태를 판단한다")
    void givenMissionWithoutChat_whenTimePasses_thenOpensAndClosesIndependently() {
        RoomMission mission = mission(null);

        assertThat(mission.statusAt(startsAt.plusHours(1), room)).isEqualTo(MissionStatus.SCHEDULED);
        assertThat(mission.statusAt(startsAt.plusHours(2), room)).isEqualTo(MissionStatus.OPEN);
        assertThat(mission.statusAt(room.endsAt(), room)).isEqualTo(MissionStatus.CLOSED);
    }

    @Test
    @DisplayName("마감 미지정이면 방 종료를 따르고 별도 마감이 있으면 그 시각에 닫힌다")
    void givenOptionalDeadline_whenRead_thenUsesRoomEndOrEarlierDeadline() {
        assertThat(mission(null).effectiveClosesAt(room)).isEqualTo(room.endsAt());
        assertThat(mission(startsAt.plusHours(5)).statusAt(startsAt.plusHours(5), room))
                .isEqualTo(MissionStatus.CLOSED);
        assertThat(mission(startsAt.plusHours(100)).effectiveClosesAt(room)).isEqualTo(room.endsAt());
    }

    @Test
    @DisplayName("미션 취소는 시작 전후 모두 상태에 반영되며 이미 기록된 내용은 보존한다")
    void givenMission_whenCancel_thenKeepsContentAndReturnsCanceled() {
        RoomMission canceled = mission(null).cancel("admin:2", startsAt.plusHours(3));

        assertThat(canceled.statusAt(startsAt.plusHours(4), room)).isEqualTo(MissionStatus.CANCELED);
        assertThat(canceled.opensAt()).isEqualTo(startsAt.plusHours(2));
        assertThat(canceled.updatedBy()).isEqualTo("admin:2");
    }

    @Test
    @DisplayName("명시한 마감은 시작 이후여야 하지만 기존 편성의 방 종료 후 시작 시각은 보존한다")
    void givenSchedule_whenValidate_thenRejectsInvalidExplicitDeadlineOnly() {
        assertThatThrownBy(() -> mission(startsAt.plusHours(2))).isInstanceOf(BadRequestException.class);
        RoomMission legacy = RoomMission.create(1L, MissionType.ANNOUNCEMENT,
                room.endsAt().plusHours(1), null, "admin:1");
        assertThat(legacy.statusAt(room.endsAt(), room)).isEqualTo(MissionStatus.CLOSED);
    }

    private RoomMission mission(LocalDateTime closesAt) {
        return RoomMission.create(1L, MissionType.ANNOUNCEMENT,
                startsAt.plusHours(2), closesAt, "admin:1");
    }
}
