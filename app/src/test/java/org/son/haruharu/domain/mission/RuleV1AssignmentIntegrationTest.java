package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.MissionAssignmentBatchService;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.son.haruharu.domain.mission.storage.MissionJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEligibilityEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionEligibilityJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionJpaRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
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

/**
 * Rule V1 이 실제 배선에서 동작하고, 배정 시점 방 상태가 결정 로그에 저장되는지 확인한다.
 * <p>
 * 정책 자체의 분기는 단위 테스트가 덮는다. 여기서는 단위 테스트가 볼 수 없는 것만 본다 —
 * 설정으로 고른 정책이 실제로 주입되는지, 컨텍스트 팩토리가 방 상태를 채우는지,
 * 그 값이 jsonb 컬럼에 저장되고 다시 읽히는지.
 */
@SpringBootTest(properties = {
        "haruharu.application.commit-sha=test-sha",
        "haruharu.mission.assignment.policy=RULE_V1",
        "spring.datasource.url=jdbc:h2:mem:rule_v1_assignment_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RuleV1AssignmentIntegrationTest {

    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired RoomMissionSlotService slotService;
    @Autowired MissionJpaRepository missionJpaRepository;
    @Autowired MissionRevisionJpaRepository revisionJpaRepository;
    @Autowired MissionRevisionEligibilityJpaRepository eligibilityJpaRepository;
    @Autowired MissionAssignmentBatchService batchService;
    @Autowired RoomMissionReader roomMissionReader;

    @Test
    @DisplayName("설정한 RULE_V1 정책이 주입되고 배정 시점 방 상태가 결정 로그에 저장된다")
    void givenRuleV1Policy_whenRunJitBatch_thenRecordsPolicyAndRoomState() {
        // given — 6인 방, Day 1 오전에 쓸 수 있는 얕은 미션 하나
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        Room room = persistRoomWithDefaultSlots(now.minusHours(2).minusMinutes(30));
        for (int index = 0; index < 6; index++) {
            roomMemberWriter.save(RoomMember.of(
                    room.id(), 7_000L + index, index % 2 == 0 ? "MALE" : "FEMALE", null,
                    now.minusDays(1)));
        }
        persistMission("오늘의 나를 보여주세요", MissionAttributeLevel.LOW, MissionGoal.OBSERVATION);

        // when
        var result = batchService.processDueSlots(now, Duration.ofMinutes(60), 100);

        // then
        assertThat(result.assigned()).isEqualTo(1);
        List<RoomMission> assigned = roomMissionReader.getByRoomId(room.id());
        assertThat(assigned).hasSize(1);

        RoomMission roomMission = assigned.getFirst();
        assertThat(roomMission.policyVersion()).isEqualTo("RULE_V1");
        assertThat(roomMission.relaxationLevel()).isEqualTo(MissionAssignmentRelaxationLevel.NONE);
        assertThat(roomMission.contextSchemaVersion()).isEqualTo("context-v2");

        assertThat(roomMission.contextSnapshot().roomState()).isNotNull();
        assertThat(roomMission.contextSnapshot().roomState().memberCount()).isEqualTo(6);
        assertThat(roomMission.contextSnapshot().roomState().closedMissionCount())
                .as("첫 미션이므로 확정된 outcome 이 아직 없다").isZero();
    }

    @Test
    @DisplayName("Day 1 상한을 넘는 깊은 미션은 eligibility 가 열려 있어도 배정되지 않는다")
    void givenDeepMissionEligibleForDayOne_whenRunJitBatch_thenUsesShallowOne() {
        // given — 운영자가 실수로 HIGH 깊이 미션에 Day 1 eligibility 를 준 상황
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 11, 30);
        Room room = persistRoomWithDefaultSlots(now.minusHours(2).minusMinutes(30));
        roomMemberWriter.save(RoomMember.of(room.id(), 7100L, "MALE", null, now.minusDays(1)));
        Long deepRevisionId = persistMission(
                "친해지면 알아줬으면 하는 나", MissionAttributeLevel.HIGH, MissionGoal.PERSONAL);
        Long shallowRevisionId = persistMission(
                "지금 있는 공간을 보여주세요", MissionAttributeLevel.LOW, MissionGoal.OBSERVATION);

        // when
        batchService.processDueSlots(now, Duration.ofMinutes(60), 100);

        // then
        RoomMission roomMission = roomMissionReader.getByRoomId(room.id()).getFirst();
        assertThat(roomMission.missionRevisionId())
                .as("Day 1 상한(LOW)을 넘는 revision 은 정책이 막아야 한다")
                .isEqualTo(shallowRevisionId)
                .isNotEqualTo(deepRevisionId);
        assertThat(roomMission.candidateSnapshot().candidates())
                .extracting(candidate -> candidate.missionRevisionId())
                .containsExactly(shallowRevisionId);
    }

    // --- fixtures ---

    private Room persistRoomWithDefaultSlots(LocalDateTime startsAt) {
        LocalDateTime selectionOpensAt = startsAt.plusHours(63);
        Room room = roomWriter.save(Room.createScheduled(
                startsAt, selectionOpensAt, selectionOpensAt.plusHours(2),
                selectionOpensAt.plusHours(3), startsAt.plusHours(72), 3));
        slotService.createDefaultsForRoom(
                room.id(), room.totalDays(), room.startsAt(), room.finalSelectionOpensAt());
        return room;
    }

    private Long persistMission(String description, MissionAttributeLevel depth, MissionGoal goal) {
        Mission logical = missionJpaRepository.save(MissionEntity.from(Mission.create())).toDomain();
        MissionRevision revision = MissionRevision.create(
                logical.id(), 1, description, depth, goal,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "CLUSTER_" + logical.id(), new BigDecimal("0.8"), false);
        MissionRevision saved = revisionJpaRepository.save(MissionRevisionEntity.from(revision)).toDomain();
        eligibilityJpaRepository.save(MissionRevisionEligibilityEntity.from(
                MissionRevisionEligibility.of(saved.id(), 1, MissionPeriod.MORNING)));
        missionJpaRepository.save(MissionEntity.from(logical.activateRevision(saved.id())));
        return saved.id();
    }
}
