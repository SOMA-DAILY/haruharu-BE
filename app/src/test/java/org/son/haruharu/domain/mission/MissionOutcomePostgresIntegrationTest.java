package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.business.MissionOutcomeBatchResult;
import org.son.haruharu.domain.mission.business.MissionOutcomeBatchService;
import org.son.haruharu.domain.mission.business.MissionOutcomeService;
import org.son.haruharu.domain.mission.business.MissionMemberStateInitializer;
import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionAssignmentContextSnapshot;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionCandidateSnapshot;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionMemberStateReader;
import org.son.haruharu.domain.mission.implement.MissionMemberStateSummary;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.mission.implement.MissionOutcome;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomStateSnapshot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.son.haruharu.domain.mission.storage.MissionJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionOutcomeJpaRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MissionOutcomePostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        // 배치 런처가 테스트 중 자동 실행돼 대상을 가로채지 않도록 끈다.
        registry.add("haruharu.mission.outcome.enabled", () -> "false");
        registry.add("haruharu.mission.assignment.enabled", () -> "false");
    }

    private static long userSequence = 9_000L;

    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired RoomMissionSlotService slotService;
    @Autowired RoomMissionWriter roomMissionWriter;
    @Autowired MissionJpaRepository missionJpaRepository;
    @Autowired MissionRevisionJpaRepository revisionJpaRepository;
    @Autowired MissionOutcomeJpaRepository outcomeJpaRepository;
    @Autowired MissionMemberStateInitializer initializer;
    @Autowired MissionMemberStateWriter stateWriter;
    @Autowired MissionMemberStateReader stateReader;
    @Autowired MissionOutcomeService outcomeService;
    @Autowired MissionOutcomeBatchService batchService;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("활동한 멤버는 노출 없이도 활성화로 집계되고, 미해소 카드는 EXPIRED로 마감된다")
    void givenMixedMemberStates_whenConfirm_thenCountsActivationSeparatelyFromResolution() {
        // given — 6인 방. m1·m2는 노출 후 활동, m3는 노출 없이 활동, m4는 dismiss, m5는 노출만, m6는 무반응
        Fixture fixture = fixture();
        List<RoomMember> members = members(fixture, 6);
        initializer.initializeOpenedTargets(fixture.opensAt().plusMinutes(1), 100);

        LocalDateTime inWindow = fixture.opensAt().plusMinutes(10);
        impress(fixture, members.get(0), inWindow);
        impress(fixture, members.get(1), inWindow);
        impress(fixture, members.get(3), inWindow);
        impress(fixture, members.get(4), inWindow);

        activate(fixture, members.get(0), inWindow.plusMinutes(1));
        activate(fixture, members.get(1), inWindow.plusMinutes(1));
        activate(fixture, members.get(2), inWindow.plusMinutes(1)); // 노출 없이 활동
        inTransaction(() -> stateWriter.dismiss(
                fixture.mission().id(), members.get(3).id(), inWindow.plusMinutes(2)));

        // when
        Optional<MissionOutcome> confirmed = inTransactionResult(
                () -> outcomeService.confirm(fixture.mission(), fixture.closesAt().plusMinutes(5)));

        // then
        assertThat(confirmed).isPresent();
        MissionOutcome outcome = confirmed.orElseThrow();
        assertThat(outcome.targetMemberCount()).isEqualTo(6);
        assertThat(outcome.impressedMemberCount()).isEqualTo(4);
        assertThat(outcome.activatedMemberCount()).isEqualTo(3);
        assertThat(outcome.dismissedMemberCount()).isEqualTo(1);
        // m3(노출 없이 활동)·m5(노출만)·m6(무반응)은 카드가 해소되지 않아 EXPIRED 로 마감된다.
        assertThat(outcome.expiredMemberCount()).isEqualTo(3);
        assertThat(outcome.roomActivationCoverage()).isEqualByComparingTo(new BigDecimal("0.5000"));
        assertThat(outcome.exposureReach()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(outcome.closesAt()).isEqualTo(fixture.closesAt());
        assertThat(outcome.metricSchemaVersion()).isEqualTo(MissionOutcome.METRIC_SCHEMA_V1);
    }

    @Test
    @DisplayName("시간창이 닫힌 뒤 발생한 활동은 방 활성화 집계에 포함되지 않는다")
    void givenActivityAfterClose_whenConfirm_thenExcludedFromActivation() {
        // given
        Fixture fixture = fixture();
        List<RoomMember> members = members(fixture, 2);
        initializer.initializeOpenedTargets(fixture.opensAt().plusMinutes(1), 100);
        impress(fixture, members.get(0), fixture.opensAt().plusMinutes(5));
        impress(fixture, members.get(1), fixture.opensAt().plusMinutes(5));

        // when — 마감 1분 뒤 활동
        activate(fixture, members.get(0), fixture.closesAt().plusMinutes(1));
        MissionMemberStateSummary summary = stateReader.summarize(fixture.mission().id());

        // then
        assertThat(summary.targetMemberCount()).isEqualTo(2);
        assertThat(summary.activatedMemberCount()).isZero();
    }

    @Test
    @DisplayName("같은 집계 버전으로 다시 확정하면 행을 덮어쓰지 않고 스킵한다")
    void givenAlreadyConfirmed_whenConfirmAgain_thenSkipsAndKeepsSingleRow() {
        // given
        Fixture fixture = fixture();
        members(fixture, 3);
        initializer.initializeOpenedTargets(fixture.opensAt().plusMinutes(1), 100);
        LocalDateTime firstRun = fixture.closesAt().plusMinutes(5);
        Optional<MissionOutcome> first = inTransactionResult(
                () -> outcomeService.confirm(fixture.mission(), firstRun));

        // when
        Optional<MissionOutcome> second = inTransactionResult(
                () -> outcomeService.confirm(fixture.mission(), firstRun.plusHours(1)));

        // then
        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        List<?> rows = outcomeJpaRepository.findAll().stream()
                .filter(entity -> entity.toDomain().roomMissionId().equals(fixture.mission().id()))
                .toList();
        assertThat(rows).hasSize(1);
        assertThat(outcomeJpaRepository
                .findByRoomMissionIdAndMetricSchemaVersion(
                        fixture.mission().id(), MissionOutcome.METRIC_SCHEMA_V1)
                .orElseThrow().toDomain().calculatedAt())
                .isEqualTo(firstRun);
    }

    @Test
    @DisplayName("배치는 정착 지연을 지난 미션만 확정하고 재실행 시 멱등하게 스킵한다")
    void givenClosedMission_whenBatchRuns_thenConfirmsOnceAndSkipsOnRerun() {
        // given
        Fixture fixture = fixture();
        members(fixture, 4);
        initializer.initializeOpenedTargets(fixture.opensAt().plusMinutes(1), 100);
        LocalDateTime now = fixture.closesAt().plusMinutes(30);

        // when
        MissionOutcomeBatchResult first =
                batchService.processClosedMissions(now, Duration.ofMinutes(5), 100);
        MissionOutcomeBatchResult second =
                batchService.processClosedMissions(now, Duration.ofMinutes(5), 100);

        // then
        assertThat(first.confirmed()).isPositive();
        assertThat(second.examined()).isZero();
        assertThat(second.confirmed()).isZero();
    }

    // --- fixtures ---

    private Fixture fixture() {
        LocalDateTime startsAt = LocalDateTime.now().minusDays(2).withNano(0);
        Room room = roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusHours(63), startsAt.plusHours(65),
                startsAt.plusHours(66), startsAt.plusHours(72), 3));
        RoomMissionSlot slot = slotService.createDefaultsForRoom(
                        room.id(), room.totalDays(), startsAt, room.finalSelectionOpensAt()).stream()
                .filter(candidate -> candidate.dayNumber() == 1 && candidate.period() == MissionPeriod.MORNING)
                .findFirst().orElseThrow();
        Mission logical = missionJpaRepository.save(MissionEntity.from(Mission.create())).toDomain();
        MissionRevision revision = MissionRevision.create(
                logical.id(), 1, "outcome 집계 미션",
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                ActivationChannel.VIDEO, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "OUTCOME_" + logical.id(), new BigDecimal("0.8"), false);
        MissionRevision savedRevision = revisionJpaRepository.save(
                MissionRevisionEntity.from(revision)).toDomain();
        LocalDateTime assignedAt = slot.opensAt().minusMinutes(30);
        // 미션 시간창을 슬롯과 동일하게 맞춰 마감 경계 판정이 슬롯 기준과 어긋나지 않게 한다.
        RoomMission mission = roomMissionWriter.save(RoomMission.assigned(
                room.id(), slot.id(), savedRevision.id(), slot.opensAt(), slot.closesAt(), 0,
                "RANDOM_V1", "catalog-test", "context-v1", assignedAt,
                new MissionAssignmentContextSnapshot(
                        room.id(), slot.id(), slot.dayNumber(), slot.period().name(),
                        slot.opensAt(), slot.closesAt(), List.of(), RoomStateSnapshot.empty(6), assignedAt),
                "candidate-v1", new MissionCandidateSnapshot(List.of(
                        new MissionCandidateSnapshot.Candidate(
                                savedRevision.id(), logical.id(), 1, false, BigDecimal.ONE))),
                BigDecimal.ONE, UUID.randomUUID(), 1L, "SPLITTABLE_RANDOM_V1",
                MissionAssignmentRelaxationLevel.NONE, "test-sha", assignedAt, 0L));
        return new Fixture(room, mission, slot.opensAt(), slot.closesAt());
    }

    private List<RoomMember> members(Fixture fixture, int count) {
        List<RoomMember> saved = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            saved.add(roomMemberWriter.save(RoomMember.of(
                    fixture.room().id(), userSequence++,
                    index % 2 == 0 ? "MALE" : "FEMALE", null,
                    fixture.opensAt().minusDays(1))));
        }
        return saved;
    }

    private void impress(Fixture fixture, RoomMember member, LocalDateTime occurredAt) {
        inTransaction(() -> stateWriter.impress(fixture.mission().id(), member.id(), occurredAt));
    }

    private void activate(Fixture fixture, RoomMember member, LocalDateTime occurredAt) {
        inTransaction(() -> stateWriter.recordActivity(
                fixture.room().id(), member.id(), ActivationChannel.VIDEO, occurredAt));
    }

    private void inTransaction(Runnable operation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> operation.run());
    }

    private <T> T inTransactionResult(java.util.function.Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    private record Fixture(Room room, RoomMission mission, LocalDateTime opensAt, LocalDateTime closesAt) {}
}
