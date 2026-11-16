package org.son.haruharu.domain.chat;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.son.haruharu.HaruharuApplication;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.son.haruharu.infrastructure.stomp.*;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** 두 실제 app 컨텍스트 + HTTP/STOMP + PostgreSQL + Valkey. 운영 처리량 벤치마크가 아닌 장애 재현이다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "haruharu.chat.fanout.redis.enabled=true")
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatMessageSyncFanoutIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("valkey/valkey:8-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired ChatService chats;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired Clock clock;
    @Autowired JwtProvider jwt;
    @Autowired JsonMapper json;
    @Autowired StompFanoutRuntime runtime;
    @Autowired ChatSubscriptionRegistry subscriptions;
    @Autowired RedisMessageListenerContainer redisListener;
    @LocalServerPort int port;

    @Test
    void givenReorderedAndLostFanout_whenSyncOverHttp_thenBothClientsConverge() throws Exception {
        var now = LocalDateTime.now(clock);
        Long roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3))).toDomain().id();
        Long userA = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        Long userB = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        for (Long user : List.of(userA, userB)) {
            members.save(RoomMemberEntity.from(new RoomMember(null, roomId, user, "MALE", "테스트", null, now)));
        }
        chats.createGroupRoom(roomId);
        Long chatId = chats.getGroupRoomBySourceRoom(roomId, userA).chatRoomId();

        try (ConfigurableApplicationContext nodeB = new SpringApplicationBuilder(HaruharuApplication.class)
                .profiles("test").run("--server.port=0", "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.datasource.driver-class-name=" + POSTGRES.getDriverClassName(),
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                        "--haruharu.chat.fanout.redis.enabled=true", "--spring.data.redis.host=" + REDIS.getHost(),
                        "--spring.data.redis.port=" + REDIS.getMappedPort(6379))) {
            int secondPort = ((WebServerApplicationContext) nodeB).getWebServer().getPort();
            var secondListener = nodeB.getBean(RedisMessageListenerContainer.class);
            await().atMost(Duration.ofSeconds(10)).until(() -> redisListener.isListening() && secondListener.isListening());
            try (var a = new Receiver(port, userA, chatId); var b = new Receiver(secondPort, userB, chatId);
                 var http = HttpClient.newHttpClient()) {
                await().atMost(Duration.ofSeconds(10)).until(() -> subscriptions.roomIds().contains(chatId)
                        && nodeB.getBean(ChatSubscriptionRegistry.class).roomIds().contains(chatId));
                var held = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                runtime.publishExecutor().execute(() -> {
                    held.countDown();
                    try {
                        if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("발행 지연 gate 시간 초과");
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
                });
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                long start = System.nanoTime();
                Long first;
                Long second;
                try {
                    first = chats.sendMessage(chatId, userA, "M1 지연 발행").messageId();
                    assertThat(a.next()).isEqualTo(first);
                    second = nodeB.getBean(ChatService.class).sendMessage(chatId, userB, "M2 먼저 수신").messageId();
                    assertThat(b.next()).isEqualTo(second);
                    assertThat(a.next()).isEqualTo(second);
                } finally {
                    release.countDown();
                }
                assertThat(b.next()).isEqualTo(first);
                long reversedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

                // WebSocket은 유지한 채 B의 Redis 구독만 중단해 복구 트리거의 필요성을 검증한다.
                secondListener.stop();
                await().atMost(Duration.ofSeconds(5)).until(() -> !secondListener.isRunning());
                Long missed = chats.sendMessage(chatId, userA, "M3 Redis 구독 단절 중").messageId();
                assertThat(a.next()).isEqualTo(missed);
                var published = new CountDownLatch(1);
                runtime.publishExecutor().execute(published::countDown);
                assertThat(published.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(b.session.isConnected()).isTrue();
                assertThat(b.received.poll(100, TimeUnit.MILLISECONDS)).isNull();
                secondListener.start();
                await().atMost(Duration.ofSeconds(10)).until(secondListener::isListening);

                var stateA = new TreeSet<>(List.of(first, second, missed));
                var stateB = new TreeSet<>(List.of(second, first));
                long cursor = 0; // 실시간으로 second를 받아도 동기화 위치는 그대로다.
                Long target = null;
                long recoveryStart = System.nanoTime();
                int pages = 0;
                do {
                    JsonNode page = fetch(http, secondPort, userB, chatId, cursor, target);
                    target = page.get("sync_target_message_id").asLong();
                    // 응답 재시도/중복도 실제 HTTP로 재현한다. 커서는 아직 전진시키지 않는다.
                    JsonNode retry = fetch(http, secondPort, userB, chatId, cursor, target);
                    assertThat(retry).isEqualTo(page);
                    page.get("messages").forEach(m -> stateB.add(m.get("message_id").asLong()));
                    cursor = page.get("next_after_message_id").asLong();
                    pages++;
                    if (!page.get("has_more").asBoolean()) break;
                    assertThat(pages).isLessThan(4);
                } while (true);
                assertThat(cursor).isEqualTo(missed);
                assertThat(stateB).containsExactlyElementsOf(stateA);
                System.out.printf("CHAT_SYNC_RECOVERY nodes=2 clients=2 committed=3 pages=%d duplicateRows=0 missingRows=0 reversedScenarioMs=%d recoveryMs=%d%n",
                        pages, reversedMs, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - recoveryStart));

                // 장애 주입 구간과 분리한 순차 전송 진단. 부하 처리량이나 운영 SLO를 주장하지 않는다.
                var deliveryMillis = new ArrayList<Long>();
                for (int i = 0; i < 20; i++) {
                    long sentAt = System.nanoTime();
                    Long id = chats.sendMessage(chatId, userA, "정상 전달 진단 " + i).messageId();
                    assertThat(a.next()).isEqualTo(id);
                    assertThat(b.next()).isEqualTo(id);
                    deliveryMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sentAt));
                }
                deliveryMillis.sort(Long::compareTo);
                System.out.printf("CHAT_SYNC_DELIVERY mode=sequential samples=20 sendCallToBothReceiversP95Ms=%d%n",
                        deliveryMillis.get(18));
            }
        }
    }

    private JsonNode fetch(HttpClient http, int serverPort, Long user, Long chatId, long after, Long target) throws Exception {
        String path = "http://127.0.0.1:" + serverPort + "/api/v1/chat-rooms/" + chatId
                + "/messages/sync?size=2&after_message_id=" + after + (target == null ? "" : "&until_message_id=" + target);
        var response = http.send(HttpRequest.newBuilder(URI.create(path))
                .header("Authorization", "Bearer " + jwt.generateAccessToken(user)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("data");
    }

    private class Receiver implements AutoCloseable {
        final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        final BlockingQueue<Long> received = new LinkedBlockingQueue<>();
        final StompSession session;
        Receiver(int serverPort, Long user, Long chatId) throws Exception {
            var converter = new StringMessageConverter();
            converter.setContentTypeResolver(headers -> null);
            client.setMessageConverter(converter);
            var headers = new StompHeaders();
            headers.set("Authorization", "Bearer " + jwt.generateAccessToken(user));
            session = client.connectAsync("ws://127.0.0.1:" + serverPort + "/ws-chat", new WebSocketHttpHeaders(),
                    headers, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
            session.subscribe("/sub/chat-rooms/" + chatId, new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders h) { return String.class; }
                @Override public void handleFrame(StompHeaders h, Object payload) {
                    received.add(json.readTree((String) payload).get("message_id").asLong());
                }
            });
        }
        Long next() throws InterruptedException {
            Long value = received.poll(10, TimeUnit.SECONDS);
            assertThat(value).as("STOMP 메시지 수신").isNotNull();
            return value;
        }
        @Override public void close() {
            if (session.isConnected()) session.disconnect();
            client.stop();
        }
    }
}
