package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.son.haruharu.domain.mission.implement.MissionMemberState;
import org.son.haruharu.domain.mission.implement.MissionMemberStateReader;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;
import org.son.haruharu.domain.mission.implement.RoomStateSnapshot;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;
import org.son.haruharu.domain.mission.implement.RoomMissionWriter;
import org.son.haruharu.domain.mission.storage.MissionEntity;
import org.son.haruharu.domain.mission.storage.MissionMemberStateJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionRevisionEntity;
import org.son.haruharu.domain.mission.storage.MissionRevisionJpaRepository;
import org.son.haruharu.domain.mission.storage.MissionJpaRepository;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MissionMemberStatePostgresIntegrationTest {

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
    }

    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired RoomMissionSlotService slotService;
    @Autowired RoomMissionWriter roomMissionWriter;
    @Autowired MissionJpaRepository missionJpaRepository;
    @Autowired MissionRevisionJpaRepository revisionJpaRepository;
    @Autowired MissionMemberStateInitializer initializer;
    @Autowired MissionMemberStateWriter stateWriter;
    @Autowired MissionMemberStateReader stateReader;
    @Autowired MissionMemberStateJpaRepository stateJpaRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mockMvc;
    @Autowired ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("커밋된 영상·GROUP 채팅 저장 이벤트는 실제 listener를 거쳐 각각의 미션 카드를 해소한다")
    void givenCommittedVideoAndGroupChatEvents_whenPublished_thenCollectsThroughRealListeners() {
        LocalDateTime videoOpen = LocalDateTime.now().minusMinutes(10);
        Fixture videoFixture = fixture(videoOpen, ActivationChannel.VIDEO);
        RoomMember videoMember = roomMemberWriter.save(RoomMember.of(
                videoFixture.room().id(), 401L, "MALE", null, videoOpen.minusDays(1)));
        initializer.initializeOpenedTargets(videoOpen.plusMinutes(1), 100);
        inTransaction(() -> stateWriter.impress(
                videoFixture.mission().id(), videoMember.id(), videoOpen.plusMinutes(2)));

        inTransaction(() -> eventPublisher.publishEvent(new VideoUploadedEvent(
                9001L, videoFixture.room().id(), videoMember.id(), videoOpen.plusMinutes(3))));

        MissionMemberState videoState = stateFor(videoMember.id());
        assertThat(videoState.resolution().name()).isEqualTo("VIDEO_UPLOAD_AFTER_VIEW");
        assertThat(videoState.expectedActivityCount()).isOne();

        LocalDateTime chatOpen = LocalDateTime.now().minusMinutes(8);
        Fixture chatFixture = fixture(chatOpen, ActivationChannel.GROUP_CHAT);
        RoomMember chatMember = roomMemberWriter.save(RoomMember.of(
                chatFixture.room().id(), 402L, "FEMALE", null, chatOpen.minusDays(1)));
        initializer.initializeOpenedTargets(chatOpen.plusMinutes(1), 100);
        inTransaction(() -> stateWriter.impress(
                chatFixture.mission().id(), chatMember.id(), chatOpen.plusMinutes(2)));

        inTransaction(() -> eventPublisher.publishEvent(new ChatMessageSentEvent(
                9100L, 700L, ChatRoomType.DIRECT, null, 402L,
                List.of(), "닉네임", chatOpen.plusMinutes(3))));
        assertThat(stateFor(chatMember.id()).expectedActivityCount()).isZero();
        inTransaction(() -> eventPublisher.publishEvent(new ChatMessageSentEvent(
                9101L, 701L, ChatRoomType.GROUP, chatFixture.room().id(), 402L,
                List.of(), "닉네임", chatOpen.plusMinutes(4))));

        MissionMemberState chatState = stateFor(chatMember.id());
        assertThat(chatState.resolution().name()).isEqualTo("CHAT_MESSAGE_AFTER_VIEW");
        assertThat(chatState.expectedActivityCount()).isOne();
    }

    @Test
    @DisplayName("실제 미션 API 흐름은 GET 무부작용, impression/dismiss 멱등, 멤버별 카드 격리를 지킨다")
    void givenOpenMission_whenUseMissionApis_thenKeepsReadOnlyAndMemberScopedResolution() throws Exception {
        LocalDateTime opensAt = LocalDateTime.now().minusMinutes(5);
        Fixture fixture = fixture(opensAt, ActivationChannel.VIDEO);
        RoomMember firstMember = roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 301L, "MALE", null, opensAt.minusDays(1)));
        roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 302L, "FEMALE", null, opensAt.minusDays(1)));
        roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 303L, "MALE", null, opensAt.plusMinutes(1)));

        mockMvc.perform(get("/api/v1/rooms/{roomId}/missions", fixture.room().id())
                        .with(authentication(auth(301L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].room_mission_id").value(fixture.mission().id()));
        mockMvc.perform(get("/api/v1/rooms/{roomId}/missions", fixture.room().id())
                        .with(authentication(auth(301L))))
                .andExpect(status().isOk());
        assertThat(statesForMission(fixture.mission().id())).isEmpty();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/rooms/{roomId}/missions/{missionId}/impression",
                            fixture.room().id(), fixture.mission().id())
                            .with(authentication(auth(301L))))
                    .andExpect(status().isOk());
        }
        assertThat(statesForMission(fixture.mission().id())).hasSize(1);
        MissionMemberState impressed = stateFor(firstMember.id());
        assertThat(impressed.firstImpressedAt()).isNotNull();
        assertThat(impressed.lastImpressedAt()).isAfterOrEqualTo(impressed.firstImpressedAt());

        mockMvc.perform(post("/api/v1/rooms/{roomId}/missions/{missionId}/dismiss",
                        fixture.room().id(), fixture.mission().id())
                        .with(authentication(auth(301L))))
                .andExpect(status().isOk());
        LocalDateTime resolvedAt = stateFor(firstMember.id()).resolvedAt();
        mockMvc.perform(post("/api/v1/rooms/{roomId}/missions/{missionId}/dismiss",
                        fixture.room().id(), fixture.mission().id())
                        .with(authentication(auth(301L))))
                .andExpect(status().isOk());
        assertThat(stateFor(firstMember.id()).resolvedAt()).isEqualTo(resolvedAt);
        mockMvc.perform(get("/api/v1/rooms/{roomId}/missions", fixture.room().id())
                        .with(authentication(auth(301L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/v1/rooms/{roomId}/missions", fixture.room().id())
                        .with(authentication(auth(302L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].room_mission_id").value(fixture.mission().id()));
        mockMvc.perform(get("/api/v1/rooms/{roomId}/missions", fixture.room().id())
                        .with(authentication(auth(303L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("지연·동시 snapshot은 opensAt 이전 가입 멤버만 정확히 한 건씩 만든다")
    void givenMembersAroundOpen_whenConcurrentCatchUp_thenSnapshotsEligibleMembersOnce() throws Exception {
        LocalDateTime opensAt = LocalDateTime.of(2026, 9, 2, 9, 0);
        Fixture fixture = fixture(opensAt, ActivationChannel.VIDEO);
        RoomMember before = roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 101L, "MALE", null, opensAt.minusMinutes(1)));
        RoomMember after = roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 102L, "FEMALE", null, opensAt.plusMinutes(1)));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<Integer> first = initializeConcurrently(opensAt.plusMinutes(10), ready, start);
        CompletableFuture<Integer> second = initializeConcurrently(opensAt.plusMinutes(10), ready, start);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);

        List<MissionMemberState> states = stateJpaRepository.findAll().stream()
                .map(entity -> entity.toDomain())
                .filter(state -> state.roomMissionId().equals(fixture.mission().id()))
                .toList();
        assertThat(states).extracting(MissionMemberState::roomMemberId).containsExactly(before.id());
        assertThat(states).noneMatch(state -> state.roomMemberId().equals(after.id()));
        int beforeRetry = statesForMission(fixture.mission().id()).size();
        initializer.initializeOpenedTargets(opensAt.plusMinutes(20), 100);
        assertThat(statesForMission(fixture.mission().id())).hasSize(beforeRetry);
    }

    @Test
    @DisplayName("지연 snapshot catch-up은 설정된 batchSize만큼 처리하고 다음 실행에서 이어간다")
    void givenMultipleEligibleTargets_whenCatchUpWithBatchSize_thenProcessesBoundedRows() {
        LocalDateTime opensAt = LocalDateTime.of(2030, 9, 2, 9, 0);
        Fixture fixture = fixture(opensAt, ActivationChannel.VIDEO);
        roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 151L, "MALE", null, opensAt.minusDays(1)));
        roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 152L, "FEMALE", null, opensAt.minusDays(1)));

        assertThat(initializer.initializeOpenedTargets(opensAt.plusMinutes(10), 1)).isOne();
        assertThat(statesForMission(fixture.mission().id())).hasSize(1);

        assertThat(initializer.initializeOpenedTargets(opensAt.plusMinutes(11), 1)).isOne();
        assertThat(statesForMission(fixture.mission().id())).hasSize(2);
        assertThat(initializer.initializeOpenedTargets(opensAt.plusMinutes(12), 1)).isZero();
    }

    @Test
    @DisplayName("동시 활동 갱신은 count를 잃지 않고 기대·교차 채널과 개인별 해소를 구분한다")
    void givenConcurrentExpectedAndCrossActivities_whenRecord_thenKeepsCountsAndMemberResolution() throws Exception {
        LocalDateTime opensAt = LocalDateTime.of(2026, 9, 3, 9, 0);
        Fixture fixture = fixture(opensAt, ActivationChannel.VIDEO);
        RoomMember firstMember = roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 201L, "MALE", null, opensAt.minusDays(1)));
        RoomMember secondMember = roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 202L, "FEMALE", null, opensAt.minusDays(1)));
        initializer.initializeOpenedTargets(opensAt.plusMinutes(1), 100);

        recordActivity(
                fixture.room().id(), firstMember.id(), ActivationChannel.VIDEO, opensAt.plusMinutes(2));
        inTransaction(() -> stateWriter.impress(
                fixture.mission().id(), firstMember.id(), opensAt.plusMinutes(3)));

        int taskCount = 20;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int index = 0; index < taskCount; index++) {
            int offset = index;
            tasks.add(CompletableFuture.runAsync(() -> {
                ready.countDown();
                await(start);
                recordActivity(
                        fixture.room().id(), firstMember.id(), ActivationChannel.GROUP_CHAT,
                        opensAt.plusMinutes(4).plusSeconds(offset));
            }, executor));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        executor.shutdown();
        recordActivity(
                fixture.room().id(), firstMember.id(), ActivationChannel.VIDEO, opensAt.plusMinutes(10));
        recordActivity(
                999_999L, firstMember.id(), ActivationChannel.VIDEO, opensAt.plusMinutes(11));
        recordActivity(
                fixture.room().id(), firstMember.id(), ActivationChannel.VIDEO, fixture.mission().closesAt());

        MissionMemberState first = stateFor(firstMember.id());
        MissionMemberState second = stateFor(secondMember.id());
        assertThat(first.expectedActivityCount()).isEqualTo(2);
        assertThat(first.crossChannelActivityCount()).isEqualTo(taskCount);
        assertThat(first.resolution().name()).isEqualTo("VIDEO_UPLOAD_AFTER_VIEW");
        assertThat(first.resolvedAt()).isEqualTo(opensAt.plusMinutes(10));
        assertThat(second.resolution()).isNull();
        assertThat(stateReader.getResolvedRoomMissionIds(
                firstMember.id(), Set.of(fixture.mission().id())))
                .containsExactly(fixture.mission().id());
        assertThat(stateReader.getResolvedRoomMissionIds(
                secondMember.id(), Set.of(fixture.mission().id()))).isEmpty();
    }

    @Test
    @DisplayName("기대 활동 UPDATE 후 더 이른 impression UPDATE가 실행돼도 이벤트 시간 기준으로 해소한다")
    void givenActivityUpdateExecutesBeforeEarlierImpression_whenRecord_thenConvergesToResolved() {
        LocalDateTime opensAt = LocalDateTime.of(2026, 9, 4, 9, 0);
        Fixture fixture = fixture(opensAt, ActivationChannel.VIDEO);
        RoomMember member = roomMemberWriter.save(RoomMember.of(
                fixture.room().id(), 501L, "MALE", null, opensAt.minusDays(1)));
        initializer.initializeOpenedTargets(opensAt.plusMinutes(1), 100);

        recordActivity(
                fixture.room().id(), member.id(), ActivationChannel.VIDEO,
                opensAt.plusMinutes(6));
        inTransaction(() -> stateWriter.impress(
                fixture.mission().id(), member.id(), opensAt.plusMinutes(5)));

        MissionMemberState state = stateFor(member.id());
        assertThat(state.resolution()).isEqualTo(
                org.son.haruharu.domain.mission.implement.MissionCardResolution.VIDEO_UPLOAD_AFTER_VIEW);
        assertThat(state.resolvedAt()).isEqualTo(opensAt.plusMinutes(6));
    }

    private CompletableFuture<Integer> initializeConcurrently(
            LocalDateTime now, CountDownLatch ready, CountDownLatch start) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            await(start);
            return initializer.initializeOpenedTargets(now, 100);
        });
    }

    private void recordActivity(
            Long roomId, Long roomMemberId, ActivationChannel channel, LocalDateTime occurredAt) {
        inTransaction(() -> stateWriter.recordActivity(roomId, roomMemberId, channel, occurredAt));
    }

    private void inTransaction(Runnable operation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> operation.run());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency start timeout");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency interrupted", interrupted);
        }
    }

    private MissionMemberState stateFor(Long roomMemberId) {
        return stateJpaRepository.findAll().stream()
                .map(entity -> entity.toDomain())
                .filter(state -> state.roomMemberId().equals(roomMemberId))
                .findFirst().orElseThrow();
    }

    private List<MissionMemberState> statesForMission(Long roomMissionId) {
        return stateJpaRepository.findAll().stream()
                .map(entity -> entity.toDomain())
                .filter(state -> state.roomMissionId().equals(roomMissionId))
                .toList();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Fixture fixture(LocalDateTime opensAt, ActivationChannel channel) {
        LocalDateTime startsAt = opensAt.minusHours(1);
        Room room = roomWriter.save(Room.createScheduled(
                startsAt, startsAt.plusHours(63), startsAt.plusHours(65),
                startsAt.plusHours(66), startsAt.plusHours(72), 3));
        RoomMissionSlot slot = slotService.createDefaultsForRoom(
                room.id(), room.totalDays(), startsAt, room.finalSelectionOpensAt()).stream()
                .filter(candidate -> candidate.period() == MissionPeriod.MORNING)
                .findFirst().orElseThrow();
        Mission logical = missionJpaRepository.save(MissionEntity.from(Mission.create())).toDomain();
        MissionRevision revision = MissionRevision.create(
                logical.id(), 1, "활동 수집 미션",
                MissionAttributeLevel.LOW, MissionGoal.OBSERVATION,
                channel, InteractionScope.SELF,
                MissionAttributeLevel.LOW, MissionAttributeLevel.LOW,
                PreferenceLeakRisk.NONE, MissionAttributeLevel.LOW,
                "STATE_" + logical.id(), new BigDecimal("0.8"), false);
        MissionRevision savedRevision = revisionJpaRepository.save(
                MissionRevisionEntity.from(revision)).toDomain();
        LocalDateTime assignedAt = opensAt.minusMinutes(30);
        RoomMission mission = roomMissionWriter.save(RoomMission.assigned(
                room.id(), slot.id(), savedRevision.id(), opensAt, opensAt.plusHours(1), 0,
                "RANDOM_V1", "catalog-test", "context-v1", assignedAt,
                new MissionAssignmentContextSnapshot(
                        room.id(), slot.id(), slot.dayNumber(), slot.period().name(),
                        opensAt, opensAt.plusHours(1), List.of(), RoomStateSnapshot.empty(6), assignedAt),
                "candidate-v1", new MissionCandidateSnapshot(List.of(
                        new MissionCandidateSnapshot.Candidate(
                                savedRevision.id(), logical.id(), 1, false, BigDecimal.ONE))),
                BigDecimal.ONE, UUID.randomUUID(), 1L, "SPLITTABLE_RANDOM_V1",
                MissionAssignmentRelaxationLevel.NONE, "test-sha", assignedAt, 0L));
        return new Fixture(room, mission);
    }

    private record Fixture(Room room, RoomMission mission) {}
}
