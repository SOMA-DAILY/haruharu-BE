package org.son.haruharu.domain.mission.common.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMissionWriter;
import org.son.haruharu.domain.mission.common.storage.exception.MissionNotFoundException;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(RoomMissionReadConsistencyIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomMissionReadConsistencyIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock missionReadClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-07T00:00:00Z"));
        @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return time.get(); }
        void at(LocalDateTime value) { time.set(value.atZone(getZone()).toInstant()); }
    }

    enum ReadPath { DETAIL, PAGE, LOOKUP }
    private final LocalDateTime opensAt = LocalDateTime.of(2026, 10, 7, 12, 0);
    @Autowired MutableClock clock;
    @Autowired RoomMissionService service;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired UserJpaRepository users;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean RoomMissionWriter writer;
    @MockitoSpyBean RoomReader roomReader;
    long roomId, userId, missionId;

    @BeforeEach
    void fixture() {
        clock.at(opensAt.minusMinutes(1));
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(opensAt.minusDays(1), opensAt.plusHours(36),
                opensAt.plusHours(40), opensAt.plusHours(42), opensAt.plusDays(2), 3))).toDomain().id();
        userId = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        members.save(RoomMemberEntity.from(RoomMember.of(roomId, userId, "MALE", LocalDate.of(2000, 1, 1), opensAt.minusDays(1))));
        missionId = service.create(roomId, command("기존 공개 내용", opensAt), "admin:1").missionId();
    }

    RoomMissionCommand command(String content, LocalDateTime start) {
        return new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition(content), start, null, null);
    }

    List<RoomMissionResponse> read(ReadPath path) {
        return switch (path) {
            case DETAIL -> List.of(service.getForParticipant(roomId, missionId, userId));
            case PAGE -> service.listForParticipant(roomId, userId, null, 10).missions();
            case LOOKUP -> service.findForParticipant(roomId, userId, List.of(missionId));
        };
    }

    @ParameterizedTest
    @EnumSource(ReadPath.class)
    void givenTimeCrossesOpeningDuringRead_whenParticipantReads_thenUsesEntryTime(ReadPath path) {
        clock.at(opensAt.minusNanos(1));
        doAnswer(invocation -> {
            Object room = invocation.callRealMethod();
            clock.at(opensAt.plusNanos(1));
            return room;
        }).when(roomReader).findById(roomId);

        if (path == ReadPath.DETAIL) {
            assertThatThrownBy(() -> read(path)).isInstanceOf(MissionNotFoundException.class);
        } else {
            assertThat(read(path)).isEmpty();
        }
        clock.at(opensAt);
        assertThat(read(path)).extracting(RoomMissionResponse::missionId).containsExactly(missionId);
    }

    @ParameterizedTest
    @EnumSource(ReadPath.class)
    void givenScheduleAndDefinitionUpdateCommitsDuringRead_whenParticipantReads_thenNeverShowsFutureDefinition(ReadPath path) throws Exception {
        verifyConcurrentRead(path, false);
    }

    @Test
    void givenAmbientReadCommittedTransaction_whenParticipantReads_thenStillNeverShowsFutureDefinition() throws Exception {
        verifyConcurrentRead(ReadPath.DETAIL, true);
    }

    // 일정과 정의는 같은 행이다. 읽는 도중 수정이 커밋되면 새 일정에 따라 통째로 숨겨지고, 옛 일정에 새 정의가 섞이지 않는다.
    private void verifyConcurrentRead(ReadPath path, boolean ambientTransaction) throws Exception {
        var updateValidated = new CountDownLatch(1);
        var allowUpdate = new CountDownLatch(1);
        var roomRead = new CountDownLatch(1);
        var allowMissionRead = new CountDownLatch(1);
        var participantThread = new AtomicReference<Thread>();
        doAnswer(invocation -> {
            updateValidated.countDown();
            await(allowUpdate);
            return invocation.callRealMethod();
        }).when(writer).save(any());
        doAnswer(invocation -> {
            Object room = invocation.callRealMethod();
            if (Thread.currentThread() == participantThread.get()) {
                roomRead.countDown();
                await(allowMissionRead);
            }
            return room;
        }).when(roomReader).findById(roomId);

        var executor = Executors.newFixedThreadPool(2);
        try {
            // The writer passes the pre-start edit guard, then pauses before committing the row.
            var update = executor.submit(() -> service.update(roomId, missionId,
                    command("다음 시간의 비공개 내용", opensAt.plusHours(1)), "admin:2"));
            await(updateValidated);
            clock.at(opensAt.plusSeconds(1));
            var reading = executor.submit(() -> {
                participantThread.set(Thread.currentThread());
                try {
                    if (!ambientTransaction) return read(path);
                    var outer = new TransactionTemplate(transactions);
                    outer.setReadOnly(true);
                    outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    return outer.execute(status -> read(path));
                } catch (MissionNotFoundException hidden) {
                    return List.<RoomMissionResponse>of();
                }
            });
            await(roomRead);
            allowUpdate.countDown();
            update.get(10, TimeUnit.SECONDS); // Commits the schedule and the definition in one row.
            allowMissionRead.countDown();

            assertThat(reading.get(10, TimeUnit.SECONDS)).isEmpty();
            var stored = service.get(roomId, missionId);
            assertThat(stored.opensAt()).isEqualTo(opensAt.plusHours(1));
            assertThat(stored.details()).isEqualTo(new AnnouncementMissionDefinition("다음 시간의 비공개 내용"));
            assertThatThrownBy(() -> service.getForParticipant(roomId, missionId, userId))
                    .isInstanceOf(MissionNotFoundException.class);
            clock.at(opensAt.plusHours(1));
            assertThat(read(path)).extracting(RoomMissionResponse::details)
                    .containsExactly(new AnnouncementMissionDefinition("다음 시간의 비공개 내용"));
        } finally {
            allowUpdate.countDown();
            allowMissionRead.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertThat(latch.await(10, TimeUnit.SECONDS)).as("concurrent transaction reached the expected step").isTrue();
    }
}
