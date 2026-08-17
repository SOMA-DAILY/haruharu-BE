package org.son.haruharu.domain.mission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.RoomMissionService;
import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionWriter;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 자동 배정 end-to-end. PENDING→ACTIVE 전이(RoomStartedEvent 커밋 후)에서
 * MissionAutoAssignListener 가 방별 미션 인스턴스를 배정하는지 실제 DB로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MissionAutoAssignIntegrationTest {

    @Autowired RoomStatusReconciler reconciler;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired MissionWriter missionWriter;
    @Autowired RoomMissionReader roomMissionReader;
    @Autowired RoomMissionService roomMissionService;

    private Room persistActivatingRoom(LocalDateTime startsAt) {
        return roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusDays(2).withHour(18), startsAt.plusDays(2).withHour(21),
                startsAt.plusDays(2).withHour(22), startsAt.plusDays(3).withHour(9), 3));
    }

    @Test
    @DisplayName("방이 ACTIVE로 전이되면 방별 미션 인스턴스가 자동 배정되고, 현재 열린 미션이 조회된다")
    void givenRoomActivates_thenMissionsAssignedAndOpenOnesQueryable() {
        // 미션 문구 풀 시드(시더와 별개로 테스트 격리를 위해 명시 저장)
        missionWriter.saveAll(List.of(
                Mission.of("미션A", 5), Mission.of("미션B", 5), Mission.of("미션C", 5),
                Mission.of("미션D", 5), Mission.of("미션E", 5), Mission.of("미션F", 5)));

        // starts_at 4시간 전 → 이미 ACTIVE 대상. 오전 카드(+3h)는 열렸고 오후 카드(+9h)는 아직.
        LocalDateTime startsAt = LocalDateTime.now().minusHours(4);
        Room room = persistActivatingRoom(startsAt);
        Long userId = 950L;
        roomMemberWriter.saveAll(List.of(
                RoomMember.of(room.id(), userId, "MALE", LocalDate.of(2000, 1, 1), startsAt)));

        reconciler.reconcile(LocalDateTime.now()); // PENDING→ACTIVE 커밋 → RoomStartedEvent → 미션 배정

        // 3일×2 = 6개 배정
        List<RoomMission> all = roomMissionReader.getOpenMissions(room.id(), startsAt.plusDays(3)); // 먼 미래 → 전부 열림
        assertThat(all).hasSize(6);
        // 지금(startsAt+4h) 기준: Day1 오전(+3h)만 열리고 오후(+9h)는 미개방
        List<RoomMissionResponse> openNow = roomMissionService.getOpenMissions(room.id(), userId);
        assertThat(openNow).hasSize(1);
        assertThat(openNow.get(0).sequence()).isZero();
        assertThat(openNow.get(0).description()).isNotBlank();
    }
}
