package org.son.haruharu.admin.domain.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.room.business.AdminRoomCommandService;
import org.son.haruharu.admin.domain.room.business.command.UpdateAdminRoomTypeCommand;
import org.son.haruharu.domain.room.business.exception.RoomAlreadyEndedException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminRoomCancelPostgresConcurrencyIntegrationTest {

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

    @Autowired AdminRoomCommandService adminRoomCommandService;
    @Autowired RoomReader roomReader;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomJpaRepository roomJpaRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE rooms RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("admin cancel이 scalar 존재 확인 뒤 room lock을 기다리면 동시 ENDED 상태를 덮어쓰지 않는다")
    void givenRoomEndsAfterAdminExistenceGate_whenCancelAcquiresLock_thenEndedWins() throws Exception {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 1, 9, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 9, 4, 9, 0);
        Long roomId = roomWriter.save(new Room(null, RoomStatus.ACTIVE, LocalDate.of(2026, 9, 1),
                3, startedAt, null)).id();
        CountDownLatch roomLocked = new CountDownLatch(1);
        CountDownLatch allowEndCommit = new CountDownLatch(1);
        AtomicReference<Throwable> lockerFailure = new AtomicReference<>();
        AtomicReference<Throwable> cancelFailure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<?> locker = pool.submit(() -> {
                try {
                    transaction.executeWithoutResult(status -> {
                        Room locked = roomReader.findByIdForUpdate(roomId).orElseThrow();
                        roomLocked.countDown();
                        await(allowEndCommit);
                        roomWriter.save(locked.ended(endedAt));
                    });
                } catch (Throwable t) {
                    lockerFailure.set(t);
                }
            });
            assertThat(roomLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> cancel = pool.submit(() -> {
                try {
                    adminRoomCommandService.cancelRoom(roomId);
                } catch (Throwable t) {
                    cancelFailure.set(t);
                }
            });
            assertThat(awaitDatabaseLockWait()).isTrue();

            allowEndCommit.countDown();
            locker.get(10, TimeUnit.SECONDS);
            cancel.get(10, TimeUnit.SECONDS);

            assertThat(lockerFailure.get()).isNull();
            assertThat(cancelFailure.get()).isInstanceOf(RoomAlreadyEndedException.class);
            assertThat(roomJpaRepository.findById(roomId).orElseThrow().toDomain().status())
                    .isEqualTo(RoomStatus.ENDED);
        } finally {
            allowEndCommit.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("방 구분 변경이 상태 전이의 락을 기다리면 최신 종료 상태를 보존한다")
    void givenConcurrentEnd_whenUpdatingRoomType_thenPreservesEndedState() throws Exception {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 1, 9, 0);
        Long roomId = roomWriter.save(new Room(null, RoomStatus.ACTIVE, startedAt.toLocalDate(),
                3, startedAt, null)).id();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> end = pool.submit(() -> transaction.executeWithoutResult(status -> {
                Room room = roomReader.findByIdForUpdate(roomId).orElseThrow();
                locked.countDown();
                await(release);
                roomWriter.save(room.ended(startedAt.plusDays(3)));
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> update = pool.submit(() -> adminRoomCommandService.updateType(roomId,
                    new UpdateAdminRoomTypeCommand(true)));
            assertThat(awaitDatabaseLockWait()).isTrue();
            release.countDown();
            end.get(10, TimeUnit.SECONDS);
            update.get(10, TimeUnit.SECONDS);
            Room saved = roomJpaRepository.findById(roomId).orElseThrow().toDomain();
            assertThat(saved.status()).isEqualTo(RoomStatus.ENDED);
            assertThat(saved.endedAt()).isEqualTo(startedAt.plusDays(3));
            assertThat(saved.isTest()).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean awaitDatabaseLockWait() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            Long waiting = jdbcTemplate.queryForObject("""
                    select count(*)
                    from pg_stat_activity
                    where datname = current_database()
                      and wait_event_type = 'Lock'
                    """, Long.class);
            if (waiting != null && waiting > 0) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", e);
        }
    }
}
