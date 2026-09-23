package org.son.haruharu.demo.business;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.MissionAssignmentIncidentService;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.storage.MissionAssignmentIncidentJpaRepository;
import org.son.haruharu.domain.mission.storage.RoomMissionSettingJpaRepository;
import org.son.haruharu.domain.mission.storage.RoomMissionSlotJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:demo_room_cleaner_mission_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoRoomCleanerMissionIncidentIntegrationTest {

    @Autowired RoomWriter roomWriter;
    @Autowired RoomMissionSlotService slotService;
    @Autowired MissionAssignmentIncidentService incidentService;
    @Autowired DemoRoomCleaner cleaner;
    @Autowired MissionAssignmentIncidentJpaRepository incidentRepository;
    @Autowired RoomMissionSlotJpaRepository slotRepository;
    @Autowired RoomMissionSettingJpaRepository settingRepository;
    @Autowired RoomJpaRepository roomRepository;

    @Test
    @DisplayName("미션 배정 incident가 있는 데모 방도 FK 위반 없이 하드 삭제한다")
    void givenRoomWithMissionIncident_whenDeleteCascade_thenDeletesIncidentBeforeSlot() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 2, 9, 0);
        Room room = roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusHours(63), startsAt.plusHours(65),
                startsAt.plusHours(66), startsAt.plusHours(72), 3));
        RoomMissionSlot slot = slotService.createDefaultsForRoom(
                room.id(), 3, startsAt, startsAt.plusHours(63)).getFirst();
        incidentService.record(
                slot, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.SAFE_FALLBACK_MISSING,
                "NoMissionCandidateException", startsAt.plusHours(2));

        assertThat(settingRepository.findByRoomId(room.id())).isPresent();

        cleaner.deleteRoomCascade(room.id());

        assertThat(incidentRepository.findByRoomMissionSlotId(slot.id())).isEmpty();
        assertThat(slotRepository.findById(slot.id())).isEmpty();
        assertThat(settingRepository.findByRoomId(room.id())).isEmpty();
        assertThat(roomRepository.findById(room.id())).isEmpty();
    }
}
