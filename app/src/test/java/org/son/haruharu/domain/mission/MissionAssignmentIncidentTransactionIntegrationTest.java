package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.MissionAssignmentIncidentService;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.storage.MissionAssignmentIncidentJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:mission_assignment_incident_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@Import(MissionAssignmentIncidentTransactionIntegrationTest.Config.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MissionAssignmentIncidentTransactionIntegrationTest {

    @Autowired RoomWriter roomWriter;
    @Autowired RoomMissionSlotService slotService;
    @Autowired FailingAssignmentTransaction failingTransaction;
    @Autowired MissionAssignmentIncidentService incidentService;
    @Autowired MissionAssignmentIncidentJpaRepository incidentRepository;

    @Test
    @DisplayName("배정 트랜잭션이 롤백돼도 REQUIRES_NEW incident는 남고 후속 성공 시 RESOLVED가 된다")
    void givenAssignmentRollback_whenRecordIncident_thenPersistsAndResolvesLater() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 2, 9, 0);
        Room room = roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusHours(63), startsAt.plusHours(65),
                startsAt.plusHours(66), startsAt.plusHours(72), 3));
        RoomMissionSlot slot = slotService.createDefaultsForRoom(
                room.id(), 3, startsAt, startsAt.plusHours(63)).get(0);
        LocalDateTime failedAt = startsAt.plusHours(2);

        assertThatThrownBy(() -> failingTransaction.failAfterIncident(slot, failedAt))
                .isInstanceOf(IllegalStateException.class);

        var persisted = incidentRepository.findByRoomMissionSlotId(slot.id()).orElseThrow().toDomain();
        assertThat(persisted.status()).isEqualTo(MissionAssignmentIncidentStatus.RETRYING);
        assertThat(persisted.attemptCount()).isOne();

        LocalDateTime resolvedAt = failedAt.plusMinutes(5);
        incidentService.resolve(slot.id(), resolvedAt);

        var resolved = incidentRepository.findByRoomMissionSlotId(slot.id()).orElseThrow().toDomain();
        assertThat(resolved.status()).isEqualTo(MissionAssignmentIncidentStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isEqualTo(resolvedAt);
    }

    @TestConfiguration
    static class Config {
        @Bean
        FailingAssignmentTransaction failingAssignmentTransaction(
                MissionAssignmentIncidentService incidentService) {
            return new FailingAssignmentTransaction(incidentService);
        }
    }

    static class FailingAssignmentTransaction {
        private final MissionAssignmentIncidentService incidentService;

        FailingAssignmentTransaction(MissionAssignmentIncidentService incidentService) {
            this.incidentService = incidentService;
        }

        @Transactional
        public void failAfterIncident(RoomMissionSlot slot, LocalDateTime now) {
            incidentService.record(
                    slot, MissionAssignmentIncidentStatus.RETRYING,
                    MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                    "SyntheticStorageFailure", now);
            throw new IllegalStateException("synthetic assignment rollback");
        }
    }
}
