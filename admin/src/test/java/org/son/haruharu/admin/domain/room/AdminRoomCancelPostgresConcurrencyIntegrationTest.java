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
    @Autowired org.son.haruharu.domain.chat.storage.ChatRoomJpaRepository chatRooms;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE rooms RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE chat_rooms RESTART IDENTITY CASCADE");
    }

    @Test
    void adminCancellationClosesChatInTheSameTransaction() {
        var now = LocalDateTime.now();
        Long roomId = roomWriter.save(Room.createScheduled(now.minusHours(1), now.plusHours(1),
                now.plusHours(2), now.plusHours(3), now.plusDays(1), 3)
                .materialized(RoomStatus.ACTIVE)).id();
        Long chatId = chatRooms.save(org.son.haruharu.domain.chat.storage.ChatRoomEntity.from(
                org.son.haruharu.domain.chat.implement.ChatRoom.createGroup(roomId))).toDomain().id();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adminRoomCommandService.cancelRoom(roomId);
            assertThat(chatRooms.findById(chatId).orElseThrow().toDomain().closeAt()).isNotNull();
            status.setRollbackOnly();
        });
        assertThat(roomReader.findById(roomId).orElseThrow().status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(chatRooms.findById(chatId).orElseThrow().toDomain().closeAt()).isNull();

        adminRoomCommandService.cancelRoom(roomId);
        assertThat(roomReader.findById(roomId).orElseThrow().status()).isEqualTo(RoomStatus.CANCELLED);
        var closeAt = chatRooms.findById(chatId).orElseThrow().toDomain().closeAt();
        assertThat(closeAt).isNotNull();
        adminRoomCommandService.cancelRoom(roomId);
        assertThat(chatRooms.findById(chatId).orElseThrow().toDomain().closeAt()).isEqualTo(closeAt);
    }

    @Test
    @DisplayName("admin 취소는 같은 트랜잭션에서 그 방의 열린 1:1 대화(PAIR)도 닫고, 이미 닫힌 방·다른 방의 PAIR 는 그대로 둔다(#540)")
    void givenStartedRoomWithPairChats_whenAdminCancels_thenClosesOnlyItsOpenPairChats() {
        var now = LocalDateTime.now();
        Long roomId = startedRoom(now);
        Long otherRoomId = startedRoom(now);
        LocalDateTime closedEarlier = now.minusMinutes(30).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Long openPair = pairChat(roomId, 1L, 2L, null);
        Long closedPair = pairChat(roomId, 1L, 3L, closedEarlier);
        Long otherRoomPair = pairChat(otherRoomId, 1L, 2L, null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            adminRoomCommandService.cancelRoom(roomId);
            assertThat(closeAt(openPair)).isNotNull();
            status.setRollbackOnly();
        });
        assertThat(closeAt(openPair)).isNull();

        adminRoomCommandService.cancelRoom(roomId);
        var firstCloseAt = closeAt(openPair);
        assertThat(firstCloseAt).isNotNull();
        assertThat(closeAt(closedPair)).isEqualTo(closedEarlier);
        assertThat(closeAt(otherRoomPair)).isNull();
        adminRoomCommandService.cancelRoom(roomId);
        assertThat(closeAt(openPair)).isEqualTo(firstCloseAt);
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

    private Long startedRoom(LocalDateTime now) {
        return roomWriter.save(Room.createScheduled(now.minusHours(1), now.plusHours(1),
                now.plusHours(2), now.plusHours(3), now.plusDays(1), 3)
                .materialized(RoomStatus.ACTIVE)).id();
    }

    private Long pairChat(Long roomId, Long userA, Long userB, LocalDateTime closeAt) {
        var chat = org.son.haruharu.domain.chat.implement.ChatRoom.createPair(roomId);
        Long chatId = chatRooms.save(org.son.haruharu.domain.chat.storage.ChatRoomEntity.from(
                closeAt == null ? chat : chat.close(closeAt))).toDomain().id();
        for (Long userId : java.util.List.of(userA, userB))
            jdbcTemplate.update("insert into chat_room_members (chat_room_id, user_id, joined_at) values (?, ?, ?)",
                    chatId, userId, LocalDateTime.now());
        return chatId;
    }

    private LocalDateTime closeAt(Long chatId) {
        return chatRooms.findById(chatId).orElseThrow().toDomain().closeAt();
    }
}
