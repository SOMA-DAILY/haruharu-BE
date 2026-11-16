package org.son.haruharu.domain.chat;

import org.junit.jupiter.api.*;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.*;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatMessageSyncPostgresIntegrationTest {
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

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatService chats;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtProvider jwt;
    @Autowired org.son.haruharu.domain.block.business.BlockService blocks;
    @LocalServerPort int port;
    Long owner, other, roomId, chatId;

    @BeforeEach
    void setup() {
        var now = LocalDateTime.now(clock);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3))).toDomain().id();
        owner = user();
        other = user();
        members.save(RoomMemberEntity.from(new RoomMember(null, roomId, owner, "MALE", "테스트하나", null, now)));
        members.save(RoomMemberEntity.from(new RoomMember(null, roomId, other, "FEMALE", "테스트둘", null, now)));
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, owner).chatRoomId();
    }

    Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    Long send(String text) { return chats.sendMessage(chatId, owner, text).messageId(); }

    JsonNode sync(long after, Long until, int size) throws Exception {
        var request = get("/api/v1/chat-rooms/" + chatId + "/messages/sync")
                .param("after_message_id", Long.toString(after)).param("size", Integer.toString(size))
                .with(authentication(new UsernamePasswordAuthenticationToken(owner, null, List.of())));
        if (until != null) request.param("until_message_id", until.toString());
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    @DisplayName("복구 중 새 메시지가 생겨도 처음 정한 상한까지 오름차순으로 완료한다")
    void givenNewMessageBetweenPages_whenSync_thenKeepTarget() throws Exception {
        Long first = send("첫 메시지");
        Long second = send("두 번째");
        Long third = send("세 번째");
        JsonNode page = sync(0, null, 2);
        assertThat(page.get("messages").size()).isEqualTo(2);
        assertThat(page.at("/messages/0/message_id").asLong()).isEqualTo(first);
        assertThat(page.at("/messages/1/message_id").asLong()).isEqualTo(second);
        assertThat(page.get("sync_target_message_id").asLong()).isEqualTo(third);
        assertThat(page.get("next_after_message_id").asLong()).isEqualTo(second);
        assertThat(page.get("has_more").asBoolean()).isTrue();
        Long later = send("복구 도중 신규");
        JsonNode last = sync(second, third, 2);
        assertThat(last.get("messages").size()).isEqualTo(1);
        assertThat(last.at("/messages/0/message_id").asLong()).isEqualTo(third);
        assertThat(last.get("next_after_message_id").asLong()).isEqualTo(third);
        assertThat(last.get("has_more").asBoolean()).isFalse();
        assertThat(sync(third, null, 2).at("/messages/0/message_id").asLong()).isEqualTo(later);
    }

    @Test
    @DisplayName("빈 방과 정확한 마지막 페이지는 추가 조회 없이 완료한다")
    void givenEmptyOrExactPage_whenSync_thenComplete() throws Exception {
        JsonNode empty = sync(0, null, 2);
        assertThat(empty.get("messages").size()).isZero();
        assertThat(empty.get("next_after_message_id").asLong()).isZero();
        assertThat(empty.get("has_more").asBoolean()).isFalse();
        send("하나");
        Long last = send("둘");
        JsonNode exact = sync(0, null, 2);
        assertThat(exact.get("messages").size()).isEqualTo(2);
        assertThat(exact.get("next_after_message_id").asLong()).isEqualTo(last);
        assertThat(exact.get("has_more").asBoolean()).isFalse();
        assertThat(sync(last, null, 2).get("messages").size()).isZero();
    }

    @Test
    @DisplayName("삭제·퇴장 숨김과 ID 공백이 있어도 고정 상한까지 확인하고 반복하지 않는다")
    void givenHiddenTail_whenSync_thenAdvanceToTargetWithoutExposingHiddenMessages() throws Exception {
        Long visible = send("보임");
        Long deleted = send("삭제됨");
        Long hidden = send("퇴장 숨김");
        jdbc.update("update chat_messages set deleted_at=now() where id=?", deleted);
        jdbc.update("update chat_messages set member_removed_at=now() where id=?", hidden);
        JsonNode page = sync(0, null, 1);
        assertThat(page.get("messages").size()).isEqualTo(1);
        assertThat(page.at("/messages/0/message_id").asLong()).isEqualTo(visible);
        assertThat(page.get("sync_target_message_id").asLong()).isEqualTo(hidden);
        assertThat(page.get("next_after_message_id").asLong()).isEqualTo(hidden);
        assertThat(page.get("has_more").asBoolean()).isFalse();
        JsonNode again = sync(hidden, null, 1);
        assertThat(again.get("messages").size()).isZero();
        assertThat(again.get("next_after_message_id").asLong()).isEqualTo(hidden);
    }

    @Test
    @DisplayName("페이지 사이에 마지막 메시지가 삭제되어도 목표를 유지하고 종료한다")
    void givenDeletionBetweenPages_whenSync_thenCompleteEmptyRange() throws Exception {
        Long first = send("첫 번째");
        Long last = send("지워질 마지막");
        assertThat(sync(0, null, 1).get("has_more").asBoolean()).isTrue();
        jdbc.update("update chat_messages set deleted_at=now() where id=?", last);
        JsonNode page = sync(first, last, 1);
        assertThat(page.get("messages").size()).isZero();
        assertThat(page.get("next_after_message_id").asLong()).isEqualTo(last);
        assertThat(page.get("has_more").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("음수·역전·미래 커서와 숫자가 아닌 커서를 거부한다")
    void givenInvalidRange_whenSync_thenBadRequest() throws Exception {
        Long head = send("기준");
        for (String[] bounds : List.of(new String[]{"-1", "0"}, new String[]{head.toString(), "0"},
                new String[]{"0", Long.toString(head + 1)}, new String[]{Long.toString(head + 1), Long.toString(head + 1)})) {
            mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/sync")
                    .param("after_message_id", bounds[0]).param("until_message_id", bounds[1])
                    .with(authentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("CHAT_SYNC_CURSOR_INVALID"));
        }
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/sync").param("after_message_id", "invalid")
                .with(authentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다음 페이지에서도 멤버 권한을 검사하고 기존 과거 조회 순서는 유지한다")
    void givenMemberLeftBetweenPages_whenSync_thenForbiddenAndHistoryUnchanged() throws Exception {
        Long first = send("먼저");
        Long last = send("나중");
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages")
                .with(authentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages[0].message_id").value(last))
                .andExpect(jsonPath("$.data.messages[1].message_id").value(first));
        sync(0, null, 1);
        jdbc.update("update chat_room_members set left_at=now() where chat_room_id=? and user_id=?", chatId, owner);
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/sync")
                .param("after_message_id", first.toString()).param("until_message_id", last.toString())
                .with(authentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CHAT_NOT_MEMBER"));
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/sync")
                .with(authentication(new UsernamePasswordAuthenticationToken(user(), null, List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("미커밋 쓰기가 있는 동안 그 이후 ID가 커밋되어 복구 커서를 앞지르지 않는다")
    void givenDelayedCommit_whenConcurrentSendAndSync_thenNoMessageBelowCompletedCursor() throws Exception {
        Long before = send("이미 커밋됨");
        var allocated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Long> first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                Long id = send("먼저 저장하고 커밋 지연");
                allocated.countDown();
                awaitLatch(release);
                return id;
            }));
            assertThat(allocated.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Long> second = executor.submit(() -> send("같은 방의 다음 쓰기"));
            try {
                await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%chat_rooms%for update%'",
                        Long.class) > 0);
                assertThat(sync(0, null, 100).get("sync_target_message_id").asLong()).isEqualTo(before);
                assertThat(second.isDone()).isFalse();
                System.out.println("CHAT_SYNC_LOCK delayedCommit=true waitingWriteObserved=true waitEventType=Lock readBlocked=false");
            } finally {
                release.countDown();
            }
            Long firstId = first.get(10, TimeUnit.SECONDS);
            Long secondId = second.get(10, TimeUnit.SECONDS);
            assertThat(secondId).isGreaterThan(firstId);
            JsonNode recovered = sync(before, null, 100);
            assertThat(recovered.get("messages").size()).isEqualTo(2);
            assertThat(recovered.at("/messages/0/message_id").asLong()).isEqualTo(firstId);
            assertThat(recovered.at("/messages/1/message_id").asLong()).isEqualTo(secondId);
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("롤백으로 ID가 비어도 커밋된 메시지만 복구한다")
    void givenRolledBackMessage_whenSync_thenSkipGap() throws Exception {
        var aborted = new AtomicReference<Long>();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            aborted.set(send("롤백할 메시지"));
            status.setRollbackOnly();
        });
        Long committed = send("커밋한 메시지");
        assertThat(committed).isGreaterThan(aborted.get());
        JsonNode page = sync(0, null, 100);
        assertThat(page.get("messages").size()).isEqualTo(1);
        assertThat(page.at("/messages/0/message_id").asLong()).isEqualTo(committed);
        assertThat(page.get("has_more").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("실제 HTTP 서버가 인증된 동기화 응답과 400·401을 반환한다")
    void givenHttpClient_whenSync_thenVerifyRealWireContract() throws Exception {
        Long id = send("HTTP 복구 확인");
        try (var client = HttpClient.newHttpClient()) {
            String url = "http://127.0.0.1:" + port + "/api/v1/chat-rooms/" + chatId + "/messages/sync";
            String bearer = "Bearer " + jwt.generateAccessToken(owner);
            var response = client.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization", bearer).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).at("/data/next_after_message_id").asLong()).isEqualTo(id);
            assertThat(client.send(HttpRequest.newBuilder(URI.create(url + "?after_message_id=-1"))
                    .header("Authorization", bearer).build(), HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(400);
            assertThat(client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("DIRECT·PAIR의 상대 차단·탈퇴는 동기화에서도 기존 정책으로 거부한다")
    void givenBlockedOrWithdrawnPartner_whenSync_thenForbidden() throws Exception {
        for (String type : List.of("DIRECT", "PAIR")) {
            jdbc.update("update chat_rooms set type=? where id=?", type, chatId);
            blocks.block(owner, new org.son.haruharu.domain.block.business.command.CreateBlockCommand(other));
            mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/sync")
                    .with(authentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CHAT_PARTNER_BLOCKED"));
            blocks.unblock(owner, other);
        }
        jdbc.update("update users set status='DELETED' where id=?", other);
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/sync")
                .with(authentication(new UsernamePasswordAuthenticationToken(owner, null, List.of()))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CHAT_PARTNER_WITHDRAWN"));
    }

    @Test
    @DisplayName("다른 방의 ID는 복구 대상에 섞이지 않고 큰 페이지 요청도 100건으로 제한한다")
    void givenOtherRoomAndOversizedPage_whenSync_thenKeepRoomAndSizeBoundaries() throws Exception {
        Long first = send("내 방 메시지");
        var otherRoom = rooms.save(RoomEntity.from(Room.createScheduled(LocalDateTime.now(clock).minusHours(25),
                LocalDateTime.now(clock).plusHours(23), LocalDateTime.now(clock).plusHours(25),
                LocalDateTime.now(clock).plusHours(26), LocalDateTime.now(clock).plusHours(47), 3))).toDomain();
        chats.createGroupRoom(otherRoom.id());
        Long otherChat = jdbc.queryForObject("select id from chat_rooms where room_id=? and type='GROUP'", Long.class, otherRoom.id());
        insertMessages(otherChat, 1);
        Long last = send("다른 방 ID 다음 메시지");
        JsonNode page = sync(0, null, 100);
        assertThat(page.get("messages").size()).isEqualTo(2);
        assertThat(page.at("/messages/0/message_id").asLong()).isEqualTo(first);
        assertThat(page.at("/messages/1/message_id").asLong()).isEqualTo(last);
        assertThat(last - first).isGreaterThan(1);
        insertMessages(chatId, 101);
        assertThat(sync(0, null, Integer.MAX_VALUE).get("messages").size()).isEqualTo(100);
    }

    @Test
    @DisplayName("기존 인덱스로 복구 범위를 조회하고 페이지당 쿼리 수가 제한된다")
    void givenFiveThousandMessages_whenSync_thenUseExistingIndex() {
        insertMessages(chatId, 5000);
        jdbc.execute("analyze chat_messages");
        long after = jdbc.queryForObject("select id from chat_messages where chat_room_id=? order by id offset 4000 limit 1",
                Long.class, chatId);
        var statements = new java.util.ArrayList<String>();
        CAPTURED_SQL.set(statements);
        try {
            assertThat(chats.syncMessages(chatId, owner, after, null, 30).messages()).hasSize(30);
        } finally { CAPTURED_SQL.remove(); }
        assertThat(statements).hasSizeLessThanOrEqualTo(12);
        String range = statements.stream().filter(sql -> sql.contains("chat_messages") && sql.contains("order by")
                && sql.contains("<=")).findFirst().orElseThrow();
        long target = jdbc.queryForObject("select max(id) from chat_messages where chat_room_id=?", Long.class, chatId);
        var plan = jdbc.queryForList("explain (analyze, buffers) " + range, String.class, chatId, after, target, 31);
        assertThat(String.join("\n", plan)).contains("Index Scan using")
                .containsAnyOf("idx_chat_messages_room_id", "chat_messages_pkey");
        long cache = jdbc.queryForObject("select seqcache from pg_sequence where seqrelid=pg_get_serial_sequence('chat_messages','id')::regclass", Long.class);
        assertThat(cache).as("ID 단조 증가 전제인 sequence CACHE 1").isEqualTo(1);
        System.out.printf("CHAT_SYNC_QUERY fixtureRows=5000 pageSize=30 sqlCount=%d sequenceCache=%d%n%s%n",
                statements.size(), cache, String.join("\n", plan));
    }

    private void insertMessages(Long room, int count) {
        jdbc.update("insert into chat_messages (chat_room_id,sender_id,content,sender_type,message_type,created_at,updated_at) "
                + "select ?,?,'sync-fixture-' || n,'USER','TEXT',now(),now() from generate_series(1,?) n", room, owner, count);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("동시성 테스트 gate 대기 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
