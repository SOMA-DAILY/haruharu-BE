package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.MissionAssignmentBatchResult;
import org.son.haruharu.domain.mission.business.MissionAssignmentBatchService;
import org.son.haruharu.domain.mission.business.MissionAssignmentIncidentService;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentReason;
import org.son.haruharu.domain.mission.implement.MissionAssignmentIncidentStatus;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotReader;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.son.haruharu.domain.mission.storage.MissionAssignmentIncidentJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEligibilityEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionEligibilityJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 이 테스트는 #365 의 JIT 배정 기계 장치를 검증한다. 기본 정책이 RULE_V1 로 바뀌어도
// 대조군인 RANDOM_V1 경로가 계속 동작하는지 확인해야 하므로 정책을 고정한다.
@SpringBootTest(properties = {
        "haruharu.application.commit-sha=test-sha",
        "haruharu.mission.assignment.policy=RANDOM_V1",
        "spring.datasource.url=jdbc:h2:mem:mission_jit_assignment_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MissionJitAssignmentIntegrationTest {

    @Autowired RoomWriter roomWriter;
    @Autowired RoomMissionSlotService slotService;
    @Autowired RoomMissionSlotReader slotReader;
    @Autowired MissionJpaRepository missionJpaRepository;
    @Autowired MissionRevisionJpaRepository revisionJpaRepository;
    @Autowired MissionRevisionEligibilityJpaRepository eligibilityJpaRepository;
    @Autowired MissionAssignmentBatchService batchService;
    @Autowired MissionAssignmentIncidentService incidentService;
    @Autowired RoomMissionReader roomMissionReader;
    @Autowired MissionAssignmentIncidentJpaRepository incidentRepository;

    @Test
    @DisplayName("배정 선행 시간 안의 슬롯은 eligible current revision 하나와 재현 로그를 원자적으로 저장한다")
    void givenDueSlotAndEligibleCatalog_whenRunJitBatch_thenStoresDecisionLog() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        LocalDateTime startsAt = now.minusHours(2).minusMinutes(30);
        Room room = persistRoomWithDefaultSlots(startsAt);
        persistMission("오전 미션", false, 1, MissionPeriod.MORNING);
        persistMission("오후 전용 미션", false, 1, MissionPeriod.AFTERNOON);

        MissionAssignmentBatchResult result = batchService.processDueSlots(
                now, Duration.ofMinutes(60), 100);

        assertThat(result.assigned()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        List<RoomMission> assigned = roomMissionReader.getByRoomId(room.id());
        assertThat(assigned).hasSize(1);
        RoomMission roomMission = assigned.get(0);
        RoomMissionSlot morning = slotReader.getByRoomId(room.id()).get(0);
        assertThat(roomMission.roomMissionSlotId()).isEqualTo(morning.id());
        assertThat(roomMission.missionRevisionId()).isNotNull();
        assertThat(roomMission.policyVersion()).isEqualTo("RANDOM_V1");
        assertThat(roomMission.applicationCommitSha()).isEqualTo("test-sha");
        assertThat(roomMission.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.NONE);
        assertThat(roomMission.selectedProbability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(roomMission.contextSnapshot().roomMissionSlotId()).isEqualTo(morning.id());
        assertThat(roomMission.candidateSnapshot().candidates())
                .extracting(candidate -> candidate.missionRevisionId())
                .containsExactly(roomMission.missionRevisionId());
        assertThat(roomMissionReader.getOpenMissions(room.id(), now)).isEmpty();
        assertThat(roomMissionReader.getOpenMissions(room.id(), morning.opensAt().plusSeconds(1)))
                .hasSize(1);
    }

    @Test
    @DisplayName("취소된 방의 슬롯은 배정 및 장애 판정 대상에서 제외한다")
    void givenCancelledRoom_whenRunJitBatch_thenIgnoresItsSlots() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        Room room = persistRoomWithDefaultSlots(now.minusHours(3));
        roomWriter.save(room.cancelled());

        MissionAssignmentBatchResult result = batchService.processDueSlots(
                now, Duration.ofDays(4), 100);

        assertThat(result.examined()).isZero();
        assertThat(roomMissionReader.getByRoomId(room.id())).isEmpty();
    }

    @Test
    @DisplayName("오픈 시각을 지난 미배정 슬롯은 마감 전까지 catch-up하고 지연 시간을 저장한다")
    void givenOpenUnassignedSlot_whenRunJitBatch_thenCatchesUpWithDelay() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        Room room = persistRoomWithDefaultSlots(now.minusHours(4));
        persistMission("catch-up 미션", false, 1, MissionPeriod.MORNING);

        MissionAssignmentBatchResult result = batchService.processDueSlots(
                now, Duration.ofMinutes(60), 100);

        assertThat(result.assigned()).isEqualTo(1);
        RoomMission assigned = roomMissionReader.getByRoomId(room.id()).getFirst();
        assertThat(assigned.assignmentDelaySeconds()).isEqualTo(3_600L);
        assertThat(assigned.assignedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("일반 후보가 없으면 fallback 배정과 DEGRADED incident를 함께 커밋한다")
    void givenOnlySafeFallback_whenRunJitBatch_thenPersistsAssignmentAndDegradedIncident() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        Room room = persistRoomWithDefaultSlots(now.minusHours(2).minusMinutes(30));
        persistMission("안전 fallback", true, 1, MissionPeriod.MORNING);

        MissionAssignmentBatchResult result = batchService.processDueSlots(
                now, Duration.ofMinutes(60), 100);

        assertThat(result.assigned()).isOne();
        assertThat(result.degraded()).isOne();
        RoomMission assigned = roomMissionReader.getByRoomId(room.id()).getFirst();
        assertThat(assigned.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.SAFE_FALLBACK);
        var incident = incidentRepository.findByRoomMissionSlotId(assigned.roomMissionSlotId())
                .orElseThrow().toDomain();
        assertThat(incident.status()).isEqualTo(MissionAssignmentIncidentStatus.DEGRADED);
        assertThat(incident.reason()).isEqualTo(MissionAssignmentIncidentReason.SAFE_FALLBACK_USED);
    }

    @Test
    @DisplayName("재시도 incident가 있던 슬롯의 정상 배정은 같은 커밋에서 RESOLVED로 전환한다")
    void givenRetryingIncident_whenNormalAssignmentSucceeds_thenResolvesIncident() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        Room room = persistRoomWithDefaultSlots(now.minusHours(2).minusMinutes(30));
        RoomMissionSlot slot = slotReader.getByRoomId(room.id()).getFirst();
        incidentService.record(
                slot, MissionAssignmentIncidentStatus.RETRYING,
                MissionAssignmentIncidentReason.ASSIGNMENT_PERSISTENCE_FAILED,
                "DataAccessException", now.minusMinutes(1));
        persistMission("복구 미션", false, 1, MissionPeriod.MORNING);

        MissionAssignmentBatchResult result = batchService.processDueSlots(
                now, Duration.ofMinutes(60), 100);

        assertThat(result.assigned()).isOne();
        var incident = incidentRepository.findByRoomMissionSlotId(slot.id()).orElseThrow().toDomain();
        assertThat(incident.status()).isEqualTo(MissionAssignmentIncidentStatus.RESOLVED);
        assertThat(incident.resolvedAt()).isEqualTo(now);
    }

    private Room persistRoomWithDefaultSlots(LocalDateTime startsAt) {
        LocalDateTime selectionOpensAt = startsAt.plusHours(63);
        Room room = roomWriter.save(Room.createScheduled(
                startsAt, selectionOpensAt, selectionOpensAt.plusHours(2),
                selectionOpensAt.plusHours(3), startsAt.plusHours(72), 3));
        slotService.createDefaultsForRoom(room.id(), room.totalDays(), room.startsAt(), room.finalSelectionOpensAt());
        return room;
    }

    private void persistMission(
            String description, boolean fallback, int dayNumber, MissionPeriod period) {
        Mission logical = missionJpaRepository.save(MissionEntity.from(Mission.create())).toDomain();
        MissionRevision revision = MissionRevision.create(
                logical.id(), 1, description,
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "CLUSTER_" + logical.id(), new BigDecimal("0.8"), fallback);
        MissionRevision saved = revisionJpaRepository.save(MissionRevisionEntity.from(revision)).toDomain();
        eligibilityJpaRepository.save(MissionRevisionEligibilityEntity.from(
                MissionRevisionEligibility.of(saved.id(), dayNumber, period)));
        missionJpaRepository.save(MissionEntity.from(logical.activateRevision(saved.id())));
    }
}
