package org.son.haruharu.domain.chat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
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
class GroupChatSynchronizationPostgresIntegrationTest {

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

    @Autowired ChatService chatService;
    @Autowired ChatRoomReader chatRoomReader;
    @Autowired ChatRoomMemberReader chatRoomMemberReader;
    @Autowired RoomWriter roomWriter;
    @Autowired RoomMemberWriter roomMemberWriter;
    @Autowired RoomMemberReader roomMemberReader;
    @Autowired RoomInviteService roomInviteService;
    @Autowired RoomActivator roomActivator;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE chat_messages, chat_room_members, chat_rooms,
                    room_invites, room_members, rooms RESTART IDENTITY CASCADE
                """);
    }

    @Test
    @DisplayName("PENDING 중복 입장 이벤트는 no-op이고 방 시작 후 GROUP·membership 하나씩으로 수렴한다")
    void givenPendingMembersAndDuplicateJoinEvents_whenRoomStarts_thenCreatesOneGroupChatAndOneMembershipPerRoomMember() {
        LocalDate date = LocalDate.of(2026, 9, 2);
        Room room = roomWriter.save(Room.create(date, 3));
        LocalDateTime firstJoinedAt = LocalDateTime.of(2026, 9, 2, 8, 30);
        LocalDateTime secondJoinedAt = LocalDateTime.of(2026, 9, 2, 8, 45);
        roomMemberWriter.saveAll(List.of(
                RoomMember.of(room.id(), 101L, null, null, firstJoinedAt),
                RoomMember.of(room.id(), 102L, null, null, secondJoinedAt)));
        RoomMemberJoinedEvent duplicate = new RoomMemberJoinedEvent(room.id(), 102L, secondJoinedAt);

        publishAfterCommit(duplicate);
        publishAfterCommit(duplicate);

        assertThat(chatRoomReader.findByRoomId(room.id())).isEmpty();

        roomActivator.activate(date, LocalDateTime.of(2026, 9, 2, 9, 0));

        assertConverged(room.id());
    }

    @Test
    @DisplayName("ACTIVE·SELECTING 방의 늦은 입장 이벤트를 두 번 받아도 입장 시각이 같은 membership 하나만 남는다")
    void givenActiveOrSelectingRoom_whenLateJoinEventIsDeliveredTwice_thenCreatesOneMembershipWithRoomJoinTime() {
        int sourceId = 1100;
        for (RoomStatus status : List.of(RoomStatus.ACTIVE, RoomStatus.SELECTING)) {
            Room room = roomWriter.save(new Room(null, status, LocalDate.of(2026, 9, 2), 3,
                    LocalDateTime.of(2026, 9, 2, 9, 0), null));
            roomMemberWriter.save(RoomMember.of(room.id(), (long) sourceId, null, null,
                    LocalDateTime.of(2026, 9, 2, 9, 5)));
            chatService.synchronizeGroupRoom(room.id());

            LocalDateTime lateJoinedAt = LocalDateTime.of(2026, 9, 2, 12, sourceId % 10);
            long lateUserId = sourceId + 1L;
            roomMemberWriter.save(RoomMember.of(room.id(), lateUserId, null, null, lateJoinedAt));
            RoomMemberJoinedEvent event = new RoomMemberJoinedEvent(room.id(), lateUserId, lateJoinedAt);

            publishAfterCommit(event);
            publishAfterCommit(event);

            assertConverged(room.id());
            ChatRoom group = chatRoomReader.findByRoomId(room.id()).orElseThrow();
            assertThat(chatRoomMemberReader.findByChatRoomId(group.id()))
                    .filteredOn(member -> member.userId().equals(lateUserId))
                    .singleElement()
                    .extracting(ChatRoomMember::joinedAt)
                    .isEqualTo(lateJoinedAt);
            sourceId += 10;
        }
    }

    @Test
    @DisplayName("maxUses=8 초대코드로 여섯 명을 넘겨 입장해도 커밋 후 GROUP 채팅이 모든 멤버와 동기화된다")
    void givenEightCapacityInvite_whenSevenMembersJoin_thenGroupChatSynchronizesAllMembers() {
        Room room = roomWriter.save(new Room(null, RoomStatus.ACTIVE, LocalDate.of(2026, 9, 2), 3,
                LocalDateTime.of(2026, 9, 2, 9, 0), null));
        roomInviteService.issue(room.id(), "SYNC88", 8, null, "test");

        for (long userId = 1300L; userId < 1307L; userId++) {
            roomInviteService.join(userId, "SYNC88");
        }

        assertConverged(room.id());
    }

    @Test
    @DisplayName("퇴장한 chat membership은 입장·시작 이벤트 재생에도 재활성화되지 않는다")
    void givenLeftChatMembership_whenJoinAndStartEventsReplay_thenDoesNotReactivateMembership() {
        Room room = roomWriter.save(new Room(null, RoomStatus.ACTIVE, LocalDate.of(2026, 9, 2), 3,
                LocalDateTime.of(2026, 9, 2, 9, 0), null));
        LocalDateTime joinedAt = LocalDateTime.of(2026, 9, 2, 9, 5);
        roomMemberWriter.save(RoomMember.of(room.id(), 1201L, null, null, joinedAt));
        chatService.synchronizeGroupRoom(room.id());
        ChatRoom group = chatRoomReader.findByRoomId(room.id()).orElseThrow();
        chatService.leaveRoom(group.id(), 1201L);
        LocalDateTime leftAt = chatRoomMemberReader.findByChatRoomId(group.id()).getFirst().leftAt();

        publishAfterCommit(new RoomMemberJoinedEvent(room.id(), 1201L, joinedAt));
        publishAfterCommit(new RoomStartedEvent(room.id()));

        List<ChatRoomMember> memberships = chatRoomMemberReader.findByChatRoomId(group.id());
        assertThat(memberships).hasSize(1);
        assertThat(memberships.getFirst().leftAt()).isEqualTo(leftAt);
        assertThat(memberships.getFirst().joinedAt()).isEqualTo(joinedAt);
    }

    @Test
    @DisplayName("PostgreSQL에서 방 시작과 신규 입장이 경합해도 deadlock·누락 없이 수렴한다")
    void givenRoomStartAndMemberJoinRace_whenRepeatedOnPostgres_thenConvergesWithoutDeadlockOrMissingMembership()
            throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 2);
        for (int attempt = 0; attempt < 6; attempt++) {
            Room room = roomWriter.save(Room.create(date, 3));
            long initialUserId = 2000L + attempt * 10L;
            long joiningUserId = initialUserId + 1L;
            roomMemberWriter.save(RoomMember.of(room.id(), initialUserId, null, null,
                    LocalDateTime.of(2026, 9, 2, 8, 30).plusMinutes(attempt)));
            String code = "RC234" + (attempt + 2);
            roomInviteService.issue(room.id(), code, 6, null, "test");

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<?> join = pool.submit(() -> runAfterLatch(ready, start,
                        () -> roomInviteService.join(joiningUserId, code)));
                Future<?> activate = pool.submit(() -> runAfterLatch(ready, start,
                        () -> roomActivator.activate(date, LocalDateTime.of(2026, 9, 2, 9, 0))));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                join.get(15, TimeUnit.SECONDS);
                activate.get(15, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
                assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }

            assertConverged(room.id());
        }
    }

    private void runAfterLatch(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("start latch timed out");
            }
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("worker interrupted", e);
        }
    }

    private void publishAfterCommit(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private void assertConverged(Long roomId) {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM chat_rooms
                WHERE room_id = ? AND type = 'GROUP' AND deleted_at IS NULL
                """, Long.class, roomId)).isOne();
        ChatRoom group = chatRoomReader.findByRoomId(roomId).orElseThrow();
        List<RoomMember> sourceMembers = roomMemberReader.getByRoomId(roomId);
        List<ChatRoomMember> chatMembers = chatRoomMemberReader.findByChatRoomId(group.id());
        assertThat(chatMembers).hasSameSizeAs(sourceMembers);
        assertThat(chatMembers)
                .extracting(ChatRoomMember::userId, ChatRoomMember::joinedAt)
                .containsExactlyInAnyOrderElementsOf(sourceMembers.stream()
                        .map(member -> org.assertj.core.groups.Tuple.tuple(member.userId(), member.joinedAt()))
                        .toList());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM chat_room_members membership
                JOIN chat_rooms chat_room ON chat_room.id = membership.chat_room_id
                JOIN room_members room_member
                  ON room_member.room_id = chat_room.room_id
                 AND room_member.user_id = membership.user_id
                WHERE chat_room.room_id = ?
                  AND membership.joined_at IS DISTINCT FROM room_member.joined_at
                """, Long.class, roomId)).isZero();
    }
}
