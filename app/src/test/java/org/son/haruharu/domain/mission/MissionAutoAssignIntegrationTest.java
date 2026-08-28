package org.son.haruharu.domain.mission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.RoomMissionService;
import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.son.haruharu.domain.mission.storage.MissionJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionJpaRepository;
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
    @Autowired MissionJpaRepository missionJpaRepository;
    @Autowired MissionRevisionJpaRepository revisionJpaRepository;
    @Autowired RoomMissionReader roomMissionReader;
    @Autowired RoomMissionService roomMissionService;

    private Room persistActivatingRoom(LocalDateTime startsAt) {
        return roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusDays(2).withHour(18), startsAt.plusDays(2).withHour(21),
                startsAt.plusDays(2).withHour(22), startsAt.plusDays(3).withHour(9), 3));
    }

    @Test
    @DisplayName("방이 ACTIVE로 전이되면 방별 미션 인스턴스가 자동 배정되고, 현재 열린 미션이 조회된다")
    void givenScheduledRoom_whenItActivates_thenAssignsRevisionsAndPreservesPastCard() {
        // 빈 카탈로그가 기본이므로 테스트에서 논리 Mission + current revision을 명시적으로 만든다.
        for (String description : List.of("미션A", "미션B", "미션C", "미션D", "미션E", "미션F")) {
            persistMission(description);
        }

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
        // 첫 배정 뒤 새 revision을 발행하고 논리 미션을 비활성화해도 배정 당시 문구는 바뀌지 않는다.
        MissionRevision assignedRevision = revisionJpaRepository.findById(all.get(0).missionRevisionId())
                .orElseThrow().toDomain();
        Mission logical = missionJpaRepository.findById(assignedRevision.missionId()).orElseThrow().toDomain();
        MissionRevision nextRevision = MissionRevision.create(
                logical.id(), 2, "나중에 발행된 새 문구",
                assignedRevision.disclosureDepth(), assignedRevision.missionGoal(),
                assignedRevision.activationChannel(), assignedRevision.interactionScope(),
                assignedRevision.participationBurden(), assignedRevision.privacyRisk(),
                assignedRevision.preferenceLeakRisk(), assignedRevision.captureDifficulty(),
                assignedRevision.semanticCluster(), assignedRevision.baseQualityScore(), false);
        MissionRevision savedNext = revisionJpaRepository.save(MissionRevisionEntity.from(nextRevision)).toDomain();
        missionJpaRepository.save(MissionEntity.from(
                logical.activateRevision(savedNext.id()).changeActive(false)));

        // 지금(startsAt+4h) 기준: Day1 오전(+3h)만 열리고 오후(+9h)는 미개방
        List<RoomMissionResponse> openNow = roomMissionService.getOpenMissions(room.id(), userId);
        assertThat(openNow).hasSize(1);
        assertThat(openNow.get(0).sequence()).isZero();
        assertThat(openNow.get(0).description()).isEqualTo(assignedRevision.description());
    }

    private void persistMission(String description) {
        Mission logical = missionJpaRepository.save(MissionEntity.from(Mission.create())).toDomain();
        MissionRevision revision = MissionRevision.create(
                logical.id(), 1, description, MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "CLUSTER_" + logical.id(), new BigDecimal("0.8"), false);
        MissionRevision savedRevision = revisionJpaRepository.save(MissionRevisionEntity.from(revision)).toDomain();
        missionJpaRepository.save(MissionEntity.from(logical.activateRevision(savedRevision.id())));
    }
}
