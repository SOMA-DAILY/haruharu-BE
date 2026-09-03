package org.son.haruharu.domain.room;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.RoomStatusTransitionService;
import org.son.haruharu.domain.room.business.exception.InviteCodeNotUsableException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.storage.RoomInviteJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomInvitePostgresConcurrencyIntegrationTest {

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

    @Autowired RoomInviteService roomInviteService;
    @Autowired RoomService roomService;
    @Autowired RoomStatusTransitionService roomStatusTransitionService;
    @Autowired RoomReader roomReader;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberJpaRepository roomMemberJpaRepository;
    @Autowired RoomInviteJpaRepository roomInviteJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE room_invites, room_members, rooms RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("PostgreSQL에서 maxUses=2 방에 세 명이 경합해도 정확히 두 명만 입장하고 usage가 일치한다")
    void givenThreeUsers_whenJoinMaxUsesTwoConcurrently_thenExactlyTwoMembershipsAndUsageAgree() throws Exception {
        Long roomId = newJoinableRoom();
        roomInviteService.issue(roomId, "RACE24", 2, null, "admin");
        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        List<Runnable> tasks = new ArrayList<>();
        for (long userId = 1L; userId <= 3L; userId++) {
            long id = userId;
            tasks.add(() -> {
                try {
                    roomInviteService.join(id, "RACE24");
                    success.incrementAndGet();
                } catch (InviteCodeNotUsableException e) {
                    full.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }

        runTogether(tasks);

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(2);
        assertThat(full.get()).isOne();
        assertThat(roomMemberJpaRepository.countByRoomId(roomId)).isEqualTo(2);
        assertThat(totalUsage(roomId)).isEqualTo(2);
    }

    @Test
    @DisplayName("PostgreSQL에서 maxUses=8 방에 아홉 명이 경합해도 정확히 여덟 명만 입장한다")
    void givenNineUsers_whenJoinMaxUsesEightConcurrently_thenExactlyEightMembershipsAndUsageAgree() throws Exception {
        Long roomId = newJoinableRoom();
        roomInviteService.issue(roomId, "E8GHT8", 8, null, "admin");
        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        List<Runnable> tasks = new ArrayList<>();
        for (long userId = 1L; userId <= 9L; userId++) {
            long id = userId;
            tasks.add(() -> {
                try {
                    roomInviteService.join(id, "E8GHT8");
                    success.incrementAndGet();
                } catch (InviteCodeNotUsableException e) {
                    full.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }

        runTogether(tasks);

        assertThat(unexpected).isEmpty();
        assertThat(success.get()).isEqualTo(8);
        assertThat(full.get()).isOne();
        assertThat(roomMemberJpaRepository.countByRoomId(roomId)).isEqualTo(8);
        assertThat(totalUsage(roomId)).isEqualTo(8);
    }

    @Test
    @DisplayName("PostgreSQL에서 maxUses=1로 찬 방의 같은 유저 재시도는 모두 성공하고 membership·usage 하나만 남긴다")
    void givenOneUser_whenJoinSameFullRoomConcurrently_thenIdempotentResponsesAndSingleWrite() throws Exception {
        Long roomId = newJoinableRoom();
        roomInviteService.issue(roomId, "SAME24", 1, null, "admin");
        AtomicInteger success = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        runTogether(List.of(
                joinTask(7L, "SAME24", success, failures),
                joinTask(7L, "SAME24", success, failures),
                joinTask(7L, "SAME24", success, failures),
                joinTask(7L, "SAME24", success, failures)));

        assertThat(failures).isEmpty();
        assertThat(success.get()).isEqualTo(4);
        assertThat(roomMemberJpaRepository.countByRoomId(roomId)).isOne();
        assertThat(totalUsage(roomId)).isOne();
    }

    @Test
    @DisplayName("PostgreSQL에서 한 유저가 서로 다른 두 방에 동시에 입장할 수 있다")
    void givenOneUserAndTwoRooms_whenJoinConcurrently_thenBothMembershipsSucceed() throws Exception {
        Long roomA = newJoinableRoom();
        Long roomB = newJoinableRoom();
        roomInviteService.issue(roomA, "RMA234", 6, null, "admin");
        roomInviteService.issue(roomB, "RMB234", 6, null, "admin");
        AtomicInteger success = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        runTogether(List.of(
                joinTask(9L, "RMA234", success, failures),
                joinTask(9L, "RMB234", success, failures)));

        assertThat(failures).isEmpty();
        assertThat(success.get()).isEqualTo(2);
        assertThat(roomMemberJpaRepository.countByRoomId(roomA)).isOne();
        assertThat(roomMemberJpaRepository.countByRoomId(roomB)).isOne();
        assertThat(totalUsage(roomA)).isOne();
        assertThat(totalUsage(roomB)).isOne();
    }

    @Test
    @DisplayName("PostgreSQL에서 join·reissue·cancel 경합은 deadlock 없이 끝나고 정원·usage 일관성을 지킨다")
    void givenConcurrentJoinsReissueAndCancel_whenAllStartTogether_thenNoDeadlockAndCountsAgree() throws Exception {
        Long roomId = newJoinableRoom();
        roomInviteService.issue(roomId, "MIX234", 2, null, "admin");
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        List<Runnable> tasks = new ArrayList<>();
        for (long userId = 20L; userId < 26L; userId++) {
            long id = userId;
            tasks.add(() -> {
                try {
                    roomInviteService.join(id, "MIX234");
                } catch (InviteCodeNotUsableException ignored) {
                    // reissue 또는 cancel이 먼저 room lock을 얻은 합법적인 결과
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }
        tasks.add(() -> runAndCapture(
                () -> roomInviteService.reissueFor(roomId, 2, null, "admin"), unexpected));
        tasks.add(() -> runAndCapture(() -> roomService.cancel(roomId), unexpected));

        runTogether(tasks);

        long memberships = roomMemberJpaRepository.countByRoomId(roomId);
        assertThat(unexpected).isEmpty();
        assertThat(memberships).isLessThanOrEqualTo(2);
        assertThat(totalUsage(roomId)).isEqualTo(memberships);
    }

    @Test
    @DisplayName("관리자 취소가 커밋된 뒤 reconciler의 오래된 PENDING 스냅샷이 CANCELLED를 덮어쓰지 않는다")
    void givenStalePendingSnapshotAfterCancel_whenTransition_thenCancelledRemains() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime starts = now.minusHours(1);
        Room saved = roomWriter.save(Room.createScheduled(starts, starts.plusDays(2).withHour(20),
                starts.plusDays(2).withHour(22), starts.plusDays(2).withHour(23),
                starts.plusDays(3), 3));
        Room stalePending = roomReader.findById(saved.id()).orElseThrow();

        roomService.cancel(saved.id());
        boolean changed = roomStatusTransitionService.transition(stalePending, now);

        assertThat(changed).isFalse();
        assertThat(roomReader.findById(saved.id()).orElseThrow().status()).isEqualTo(RoomStatus.CANCELLED);
    }

    @Test
    @DisplayName("종료가 커밋된 뒤 reconciler의 오래된 PENDING 스냅샷이 ENDED를 덮어쓰지 않는다")
    void givenStalePendingSnapshotAfterEnd_whenTransition_thenEndedRemains() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime starts = now.minusHours(1);
        Room saved = roomWriter.save(Room.createScheduled(starts, starts.plusDays(2).withHour(20),
                starts.plusDays(2).withHour(22), starts.plusDays(2).withHour(23),
                starts.plusDays(3), 3));
        Room stalePending = roomReader.findById(saved.id()).orElseThrow();

        roomWriter.save(saved.ended(now));
        boolean changed = roomStatusTransitionService.transition(stalePending, now);

        assertThat(changed).isFalse();
        assertThat(roomReader.findById(saved.id()).orElseThrow().status()).isEqualTo(RoomStatus.ENDED);
    }

    private Long newJoinableRoom() {
        LocalDateTime starts = LocalDateTime.now().plusHours(1);
        return roomWriter.save(Room.createScheduled(starts, starts.plusDays(2).withHour(20),
                starts.plusDays(2).withHour(22), starts.plusDays(2).withHour(23),
                starts.plusDays(2).withHour(23).withMinute(30), 3)).id();
    }

    private long totalUsage(Long roomId) {
        return roomInviteJpaRepository.findByRoomIdOrderByIdDesc(roomId).stream()
                .mapToLong(entity -> entity.toDomain().usedCount())
                .sum();
    }

    private Runnable joinTask(Long userId, String code, AtomicInteger success, Queue<Throwable> failures) {
        return () -> {
            try {
                roomInviteService.join(userId, code);
                success.incrementAndGet();
            } catch (Throwable t) {
                failures.add(t);
            }
        };
    }

    private void runAndCapture(Runnable task, Queue<Throwable> failures) {
        try {
            task.run();
        } catch (Throwable t) {
            failures.add(t);
        }
    }

    private void runTogether(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        try {
            for (Runnable task : tasks) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("start latch timed out");
                        }
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
