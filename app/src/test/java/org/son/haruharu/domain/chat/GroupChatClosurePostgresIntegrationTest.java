package org.son.haruharu.domain.chat;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.son.haruharu.domain.chat.business.*;
import org.son.haruharu.domain.chat.business.exception.ChatRoomClosedException;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.storage.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.son.haruharu.infrastructure.stomp.ChatSubscriptionRegistry;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "haruharu.chat.subscription-reconcile-delay-ms=100")
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupChatClosurePostgresIntegrationTest {
    private static final ThreadLocal<java.util.List<String>> CAPTURED_SQL = new ThreadLocal<>();

    @org.springframework.boot.test.context.TestConfiguration
    static class SqlCaptureConfiguration {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer sqlCapture() {
            return properties -> properties.put("hibernate.session_factory.statement_inspector",
                    (org.hibernate.resource.jdbc.spi.StatementInspector) sql -> {
                        var captured = CAPTURED_SQL.get();
                        if (captured != null) captured.add(sql);
                        return sql;
                    });
        }
    }

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @LocalServerPort int port;
    @MockitoBean Clock clock;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository sourceMembers;
    @Autowired UserJpaRepository users;
    @Autowired ChatRoomJpaRepository chatRooms;
    @Autowired ChatRoomMemberJpaRepository members;
    @Autowired ChatService chats;
    @Autowired org.son.haruharu.domain.room.business.RoomService roomService;
    @Autowired ChatDeliveryService delivery;
    @Autowired AdminChatMessageService adminMessages;
    @Autowired ChatMessageReactionService reactions;
    @Autowired VideoReplyService videoReplies;
    @Autowired ChatSubscriptionRegistry subscriptions;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired JwtProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.son.haruharu.domain.room.business.RoomStatusTransitionService transitions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    org.son.haruharu.infrastructure.stomp.LocalStompDeliveryAdapter localDelivery;
    private final AtomicReference<Instant> instant = new AtomicReference<>();
    private final ZoneId zone = ZoneId.of("Asia/Seoul");
    private Instant end;
    private Long roomId, chatId, user, other;
    private WebSocketStompClient client;
    private StompSession session;

    @BeforeEach
    void setup() {
        instant.set(Instant.parse("2026-10-13T10:00:00Z"));
        end = instant.get().plusSeconds(3600);
        when(clock.getZone()).thenReturn(zone);
        when(clock.instant()).thenAnswer(invocation -> instant.get());
        LocalDateTime now = LocalDateTime.now(clock);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusDays(2), now.plusMinutes(5),
                now.plusMinutes(10), now.plusMinutes(15), now.plusHours(1), 3)
                .materialized(RoomStatus.ACTIVE))).toDomain().id();
        user = user(); other = user();
        for (Long id : new Long[]{user, other}) {
            sourceMembers.save(RoomMemberEntity.from(new RoomMember(null, roomId, id, "MALE", "민준", null, now)));
        }
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, user).chatRoomId();
    }

    @AfterEach
    void disconnect() {
        if (session != null && session.isConnected()) session.disconnect();
        if (client != null) client.stop();
    }

    @Test
    void ordinaryGroupSendAndAfterCommitDeliveryDoNotQuerySourceRooms() {
        var statements = new java.util.ArrayList<String>();
        CAPTURED_SQL.set(statements);
        try {
            chats.sendMessage(chatId, user, "추가 rooms 조회 없음");
        } finally {
            CAPTURED_SQL.remove();
        }
        assertThat(statements).anyMatch(sql -> sql.contains("chat_rooms"));
        assertThat(statements).noneMatch(sql -> java.util.regex.Pattern
                .compile("(?i)\\b(from|join)\\s+(public\\.)?rooms\\b").matcher(sql).find());
    }

    @Test
    void idleSocketClosesOnlyExpiredGroupAndRetainsHistoryOverHttp() throws Exception {
        Long directId = chatRooms.save(ChatRoomEntity.from(ChatRoom.createDirect(roomId))).toDomain().id();
        members.save(ChatRoomMemberEntity.from(org.son.haruharu.domain.chat.implement.ChatRoomMember
                .join(directId, user, LocalDateTime.now(clock))));
        String token = jwt.generateAccessToken(user);
        client = new WebSocketStompClient(new StandardWebSocketClient());
        var converter = new StringMessageConverter();
        converter.setContentTypeResolver(headers -> null); // JSON도 원문으로 검증한다.
        client.setMessageConverter(converter);
        StompHeaders connect = new StompHeaders();
        connect.set("Authorization", "Bearer " + token);
        session = client.connectAsync("ws://localhost:" + port + "/ws-chat", new WebSocketHttpHeaders(),
                connect, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
        var group = subscribe("/sub/chat-rooms/" + chatId);
        var events = subscribe("/sub/chat-rooms/" + chatId + "/events");
        var direct = subscribe("/sub/chat-rooms/" + directId);
        var personal = subscribe("/user/queue/errors");
        var notices = subscribe("/user/queue/chat-room-events");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(subscriberCount("/sub/chat-rooms/" + chatId)).isEqualTo(1);
            assertThat(subscriberCount("/sub/chat-rooms/" + chatId + "/events")).isEqualTo(1);
            assertThat(subscriberCount("/sub/chat-rooms/" + directId)).isEqualTo(1);
        });
        chats.sendMessage(chatId, user, "종료 전 대화");
        assertThat(group.poll(5, TimeUnit.SECONDS)).contains("종료 전 대화");

        // 합의한 스케줄러 지연: 예정 시각만 지나서는 close_at이 없으므로 열린 상태다.
        instant.set(end);
        assertThat(delivery.isActive(chatId)).isTrue();
        closeGroup();
        assertThat(group.poll(5, TimeUnit.SECONDS)).contains("CHAT_ROOM_CLOSED", "chat_room_id");
        assertThat(events.poll(5, TimeUnit.SECONDS)).contains("CHAT_ROOM_CLOSED");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(subscriberCount("/sub/chat-rooms/" + chatId)).isZero());
        assertThat(subscriberCount("/sub/chat-rooms/" + chatId + "/events")).isZero();
        session.subscribe("/sub/chat-rooms/" + chatId, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { group.add((String) payload); }
        });
        assertThat(notices.poll(5, TimeUnit.SECONDS)).contains("CHAT_ROOM_CLOSED");
        session.send("/pub/chat-rooms/" + chatId + "/messages", "{\"content\":\"late\"}");
        assertThat(notices.poll(5, TimeUnit.SECONDS)).contains("CHAT_ROOM_CLOSED");
        assertThat(subscriberCount("/sub/chat-rooms/" + chatId)).isZero();
        assertThat(session.isConnected()).isTrue();
        messaging.convertAndSend("/sub/chat-rooms/" + directId, "still-direct");
        messaging.convertAndSendToUser(user.toString(), "/queue/errors", "still-personal");
        assertThat(direct.poll(5, TimeUnit.SECONDS)).isEqualTo("still-direct");
        assertThat(personal.poll(5, TimeUnit.SECONDS)).isEqualTo("still-personal");

        assertThat(rooms.findById(roomId).orElseThrow().toDomain().status()).isEqualTo(RoomStatus.ENDED);
        assertThat(delivery.isActive(chatId)).isFalse();
        assertThatThrownBy(() -> chats.sendMessage(chatId, user, "late")).isInstanceOf(ChatRoomClosedException.class);
        assertThatThrownBy(() -> adminMessages.sendAsSystem(chatId, "late-admin", null))
                .isInstanceOf(ChatRoomClosedException.class);
        assertThat(members.findByChatRoomId(chatId)).allMatch(m -> !m.toDomain().hasLeft());
        assertThat(chatRooms.findById(chatId)).isPresent();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/chat-rooms/" + chatId + "/messages"))
                .header("Authorization", "Bearer " + token).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("종료 전 대화");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"message", "reaction", "video"})
    void writeWaitingForPostgresLockObservesCommittedClosureBeforeSaving(String kind) throws Exception {
        Long messageId = chats.sendMessage(chatId, user, "기존 대화").messageId();
        Runnable write = switch (kind) {
            case "reaction" -> () -> reactions.toggle(other, chatId, messageId, ReactionType.LAUGH);
            case "video" -> () -> videoReplies.send(roomId, 999L, user, "late", java.util.UUID.randomUUID().toString());
            default -> () -> chats.sendMessage(chatId, user, "late");
        };
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var locker = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("select id from chat_rooms where id = ? for update", Long.class, chatId);
                locked.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                    closeGroup();
                }
                catch (InterruptedException e) { throw new AssertionError(e); }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var sender = executor.submit(() -> assertThatThrownBy(write::run)
                    .isInstanceOf(ChatRoomClosedException.class));
            try {
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%chat_rooms%'",
                        Long.class)).isPositive());
                instant.set(end);
            } finally { release.countDown(); }
            locker.get(10, TimeUnit.SECONDS); sender.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where chat_room_id = ?", Long.class, chatId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from chat_message_reactions where message_id = ?", Long.class, messageId)).isZero();
    }

    @Test
    void closureWaitsForAnInFlightReactionToCommit() throws Exception {
        Long messageId = chats.sendMessage(chatId, user, "반응 대상").messageId();
        CountDownLatch reacted = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reaction = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                reactions.toggle(other, chatId, messageId, ReactionType.LAUGH);
                reacted.countDown();
                try { assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException e) { throw new AssertionError(e); }
            }));
            assertThat(reacted.await(10, TimeUnit.SECONDS)).isTrue();
            instant.set(end);
            var closure = executor.submit(this::closeGroup);
            try {
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like 'update chat_rooms%'",
                        Long.class)).isPositive());
            } finally { release.countDown(); }
            reaction.get(10, TimeUnit.SECONDS);
            closure.get(10, TimeUnit.SECONDS);
        }
        assertThat(delivery.isActive(chatId)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from chat_message_reactions where message_id = ?", Long.class, messageId))
                .isEqualTo(1);
    }

    @Test
    void newConnectionAfterClosureCannotResubscribe() throws Exception {
        instant.set(end);
        closeGroup();
        client = new WebSocketStompClient(new StandardWebSocketClient());
        var converter = new StringMessageConverter();
        converter.setContentTypeResolver(headers -> null);
        client.setMessageConverter(converter);
        var connect = new StompHeaders();
        connect.set("Authorization", "Bearer " + jwt.generateAccessToken(user));
        session = client.connectAsync("ws://localhost:" + port + "/ws-chat", new WebSocketHttpHeaders(),
                connect, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
        var notices = subscribe("/user/queue/chat-room-events");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            messaging.convertAndSendToUser(user.toString(), "/queue/chat-room-events", "ready");
            assertThat(notices.poll(100, TimeUnit.MILLISECONDS)).isEqualTo("ready");
        });
        subscribe("/sub/chat-rooms/" + chatId);
        assertThat(notices.poll(5, TimeUnit.SECONDS)).contains("CHAT_ROOM_CLOSED");
        assertThat(subscriberCount("/sub/chat-rooms/" + chatId)).isZero();
        assertThat(session.isConnected()).isTrue();
    }

    @Test
    void roomTransitionNotifiesOnlyAfterCommitAndRollbackPreservesHistory() {
        instant.set(end);
        var room = rooms.findById(roomId).orElseThrow().toDomain();
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            transitions.transition(room, LocalDateTime.now(clock));
            org.mockito.Mockito.verify(localDelivery, org.mockito.Mockito.never())
                    .closeRoom(org.mockito.ArgumentMatchers.eq(chatId), org.mockito.ArgumentMatchers.any());
            status.setRollbackOnly();
        });
        assertThat(rooms.findById(roomId).orElseThrow().toDomain().status()).isEqualTo(RoomStatus.ACTIVE);
        org.mockito.Mockito.verify(localDelivery, org.mockito.Mockito.never())
                .closeRoom(org.mockito.ArgumentMatchers.eq(chatId), org.mockito.ArgumentMatchers.any());
        assertThat(chatRooms.findById(chatId).orElseThrow().toDomain().closeAt()).isNull();
        assertThat(transitions.transition(room, LocalDateTime.now(clock))).isTrue();
        var firstCloseAt = chatRooms.findById(chatId).orElseThrow().toDomain().closeAt();
        assertThat(firstCloseAt).isEqualTo(LocalDateTime.now(clock));
        instant.set(end.plusSeconds(10));
        assertThat(transitions.transition(room, LocalDateTime.now(clock))).isFalse();
        assertThat(chatRooms.findById(chatId).orElseThrow().toDomain().closeAt()).isEqualTo(firstCloseAt);
        org.mockito.Mockito.verify(localDelivery).closeRoom(chatId,
                new org.son.haruharu.domain.chat.application.ChatLifecycleEventListener.RoomClosedPayload("CHAT_ROOM_CLOSED", chatId));
        assertThat(chatRooms.findById(chatId)).isPresent();
        assertThat(members.findByChatRoomId(chatId)).allMatch(m -> !m.toDomain().hasLeft());
    }

    @Test
    void archiveRejectsReactionsButKeepsMessageAndMembership() {
        Long messageId = chats.sendMessage(chatId, user, "보존할 대화").messageId();
        instant.set(end);
        closeGroup();
        assertThatThrownBy(() -> reactions.toggle(other, chatId, messageId, ReactionType.LAUGH))
                .isInstanceOf(ChatRoomClosedException.class);
        assertThat(chats.getMessages(chatId, other, null, 30).messages()).hasSize(1);
    }

    @Test
    void schedulerDelayAllowsSendUntilClosureAndThenPreservesHistory() {
        instant.set(end.plusSeconds(20));
        assertThat(delivery.isActive(chatId)).isTrue();
        chats.sendMessage(chatId, user, "스케줄러 지연 중");
        closeGroup();
        assertThat(delivery.isActive(chatId)).isFalse();
        assertThatThrownBy(() -> chats.sendMessage(chatId, user, "종료 후"))
                .isInstanceOf(ChatRoomClosedException.class);
        assertThat(chats.getMessages(chatId, user, null, 30).messages()).hasSize(1);
    }

    @Test
    void cancellationAndChatClosureCommitOrRollbackTogether() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            // 1차 캐시에 열린 채팅방이 있어도 bulk 종료 후 검사에는 닫힌 상태가 보여야 한다.
            assertThat(delivery.isActive(chatId)).isTrue();
            roomService.cancel(roomId);
            assertThat(delivery.isActive(chatId)).isFalse();
            status.setRollbackOnly();
        });
        assertThat(rooms.findById(roomId).orElseThrow().toDomain().status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(delivery.isActive(chatId)).isTrue();
        roomService.cancel(roomId);
        assertThat(rooms.findById(roomId).orElseThrow().toDomain().status()).isEqualTo(RoomStatus.CANCELLED);
        assertThat(delivery.isActive(chatId)).isFalse();
        assertThatThrownBy(() -> chats.sendMessage(chatId, user, "취소 후"))
                .isInstanceOf(ChatRoomClosedException.class);
        assertThat(members.findByChatRoomId(chatId)).allMatch(m -> !m.toDomain().hasLeft());
    }

    @Test
    void lateGroupCreationForTerminalSourceStartsClosed() {
        var now = LocalDateTime.now(clock);
        for (RoomStatus terminal : new RoomStatus[]{RoomStatus.ENDED, RoomStatus.CANCELLED}) {
            var source = Room.createScheduled(now.minusDays(3), now.minusHours(3), now.minusHours(2),
                    now.minusHours(1), now.minusMinutes(1), 3).materialized(terminal);
            Long id = rooms.save(RoomEntity.from(source)).toDomain().id();
            chats.createGroupRoom(id);
            var created = chatRooms.findByRoomIdAndType(id, org.son.haruharu.domain.chat.implement.ChatRoomType.GROUP).orElseThrow().toDomain();
            assertThat(created.closeAt()).isNotNull();
            assertThat(delivery.isActive(created.id())).isFalse();
        }
    }

    private void closeGroup() {
        transitions.transition(rooms.findById(roomId).orElseThrow().toDomain(), LocalDateTime.now(clock));
    }

    private Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    private BlockingQueue<String> subscribe(String topic) {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        session.subscribe(topic, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) { queue.add((String) payload); }
        });
        return queue;
    }

    private int subscriberCount(String topic) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(topic);
        return subscriptions.findSubscriptions(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()))
                .values().stream().mapToInt(java.util.List::size).sum();
    }
}
