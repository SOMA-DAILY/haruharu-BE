package org.son.haruharu.domain.roomschedule;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.room.business.MemberProfileService;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.roomschedule.business.RoomScheduleDispatchService;
import org.son.haruharu.domain.roomschedule.business.RoomScheduleService;
import org.son.haruharu.domain.roomschedule.business.command.ScheduleItemCommand;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.son.haruharu.infrastructure.stomp.StompDeliveryPort;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 방 편성 발송 잡과 편성 기준 공개(#457) — PostgreSQL 에서 claim 경쟁, 상태 전이, 응답 모양 확인. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoomScheduleDispatchIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }


    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired ChatService chats;
    @Autowired RoomScheduleService scheduleService;
    @Autowired RoomScheduleDispatchService dispatcher;
    @Autowired MemberProfileService memberProfiles;
    @Autowired Clock clock;
    @Autowired ChatMessageReader messageReader;
    @Autowired org.son.haruharu.domain.profile.storage.UserProfileJpaRepository profiles;
    @Autowired org.son.haruharu.utils.JwtProvider jwtProvider;
    @Autowired tools.jackson.databind.json.JsonMapper jsonMapper;
    @Autowired org.son.haruharu.domain.chat.application.ChatStompController stompController;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @MockitoBean StompDeliveryPort delivery;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader itemReader;
    @Autowired org.son.haruharu.domain.roomschedule.business.RoomScheduleDispatchStep step;

    LocalDateTime now;
    Long roomId, chatId, alice, bob, carol, bobMemberId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from room_schedule_items");
        now = LocalDateTime.now(clock);
        roomId = room(now.minusHours(25), now.plusHours(47));
        alice = user();
        bob = user();
        carol = user();
        member(alice, "윤슬");
        bobMemberId = member(bob, "선우");
        member(carol, "하린");
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, alice).chatRoomId();
    }

    Long room(LocalDateTime startsAt, LocalDateTime endsAt) {
        return rooms.save(RoomEntity.from(Room.createScheduled(startsAt, endsAt.minusHours(10), endsAt.minusHours(8),
                endsAt.minusHours(7), endsAt, 3))).toDomain().id();
    }

    Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    Long member(Long userId, String name) {
        return members.save(RoomMemberEntity.from(new RoomMember(null, roomId, userId, "MALE", name,
                LocalDate.of(2003, 1, 1), now))).toDomain().id();
    }

    Long addItem(Long room, LocalDateTime runAt, List<String> reveal, String content, boolean push) {
        return scheduleService.add(room, new ScheduleItemCommand(runAt, reveal, content, push, null, null), "admin:1").id();
    }

    Map<String, Object> item(Long id) {
        return jdbc.queryForMap("select status, fail_reason, sent_message_id, sent_at from room_schedule_items where id=?", id);
    }

    long adminMessages(Long chatRoomId) {
        return jdbc.queryForObject("select count(*) from chat_messages where chat_room_id=? and sender_type='SYSTEM'",
                Long.class, chatRoomId);
    }

    long inbox(String type) {
        return jdbc.queryForObject("select count(*) from notification_history where type=?", Long.class, type);
    }

    @Test
    @DisplayName("실제 HTTP 서버에서 인증된 참가자가 SYSTEM 메시지를 재조회한다")
    void givenCommittedSystemMessage_whenHttpGet_thenReturnsSenderContract() throws Exception {
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "HTTP 재조회", false);
        dispatcher.dispatchDue();
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://localhost:" + port + "/api/v1/chat-rooms/" + chatId + "/messages"))
                .header("Authorization", "Bearer " + jwtProvider.generateAccessToken(bob)).GET().build();
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var message = jsonMapper.readTree(response.body()).path("data").path("messages").get(0);
            assertThat(message.path("message_id").asLong()).isEqualTo(((Number) item(id).get("sent_message_id")).longValue());
            assertThat(message.path("sender_type").asText()).isEqualTo("SYSTEM");
            assertThat(message.path("sender_id").isNull()).isTrue();
            assertThat(message.path("message_type").asText()).isEqualTo("TEXT");
            assertThat(message.path("content").asText()).isEqualTo("HTTP 재조회");
        }
    }

    @Test
    @DisplayName("클라이언트가 SYSTEM과 다른 sender_id를 보내도 인증된 USER로만 저장한다")
    void givenForgedSenderPayload_whenStompSend_thenUsesAuthenticatedUser() {
        var request = jsonMapper.readValue("{\"content\":\"위조 시도\",\"sender_type\":\"SYSTEM\",\"sender_id\":999}",
                org.son.haruharu.domain.chat.application.dto.SendMessageRequest.class);
        stompController.send(chatId, request, new org.son.haruharu.config.StompPrincipal(alice));
        var saved = messageReader.findLast(chatId, Set.of()).orElseThrow();
        assertThat(saved.senderType()).isEqualTo(org.son.haruharu.domain.chat.implement.ChatSenderType.USER);
        assertThat(saved.senderId()).isEqualTo(alice);
    }

    @Test
    @DisplayName("DB 저장 실패는 메시지·SENT를 롤백하고 방송하지 않는다")
    void givenStorageFailure_whenDispatch_thenRollbackWithoutBroadcast() {
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "저장 실패", false);
        jdbc.execute("alter table chat_messages add constraint test_484_failure check (content <> '저장 실패')");
        try {
            assertThat(dispatcher.dispatchDue()).isZero();
            assertThat(item(id).get("status")).isEqualTo("FAILED");
            assertThat(item(id).get("sent_message_id")).isNull();
            assertThat(adminMessages(chatId)).isZero();
            verifyNoInteractions(delivery);
        } finally {
            jdbc.execute("alter table chat_messages drop constraint test_484_failure");
        }
    }

    @Test
    @DisplayName("커밋 후 방송이 실패해도 SENT·본문을 보존하고 다음 틱에 중복 저장하지 않는다")
    void givenDeliveryFailure_whenReadAgain_thenCommittedMessageSurvives() {
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "전달 실패 후 복원", true);
        doAnswer(call -> {
            ChatMessageBroadcast message = call.getArgument(1);
            assertThat(item(id).get("status")).isEqualTo("SENT");
            assertThat(messageReader.findById(message.messageId())).isPresent();
            throw new IllegalStateException("test delivery unavailable");
        }).when(delivery).sendToTopic(anyString(), any(ChatMessageBroadcast.class), anyString());

        dispatcher.dispatchDue();
        assertThat(item(id).get("status")).isEqualTo("SENT");
        assertThat(chats.getMessages(chatId, bob, null, 20).messages().getFirst().content()).isEqualTo("전달 실패 후 복원");
        assertThat(dispatcher.dispatchDue()).isZero();
        assertThat(adminMessages(chatId)).isEqualTo(1);
        verify(delivery, times(1)).sendToTopic(anyString(), any(ChatMessageBroadcast.class), anyString());
    }

    @Test
    @DisplayName("차단 제외·안읽음·미리보기에서도 계정 없는 시스템 안내와 삭제 표시를 보존한다")
    void givenSystemMessage_whenExcludeSenders_thenStillVisibleAndUnread() {
        chats.sendMessage(chatId, alice, "차단 대상의 메시지");
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "시스템 공지", false);
        dispatcher.dispatchDue();
        Long messageId = ((Number) item(id).get("sent_message_id")).longValue();

        assertThat(messageReader.countUnread(chatId, null, Set.of(alice, bob))).isEqualTo(1);
        assertThat(messageReader.countUnread(chatId, null, Set.of())).isEqualTo(2);
        assertThat(messageReader.findLast(chatId, Set.of(alice)).orElseThrow().id()).isEqualTo(messageId);
        chats.sendMessage(chatId, bob, "답장", messageId);
        assertThat(chats.getMessages(chatId, carol, null, 20).messages().getFirst().replyTo().available()).isTrue();
        assertThat(chats.getMessages(chatId, carol, null, 20).messages().getFirst().replyTo().senderName()).isEqualTo("하루");

        Long last = addItem(roomId, now.minusMinutes(1), List.of(), "삭제할 공지", false);
        dispatcher.dispatchDue();
        scheduleService.deleteSentMessage(roomId, last);
        for (Long userId : List.of(alice, bob, carol)) {
            profiles.save(org.son.haruharu.domain.profile.storage.UserProfileEntity.from(
                    org.son.haruharu.domain.profile.implement.UserProfile.empty(userId)));
        }
        assertThat(chats.getMyRooms(bob).chatRooms().stream().filter(r -> r.chatRoomId().equals(chatId)).findFirst().orElseThrow().lastMessage())
                .isEqualTo("삭제된 메시지");
    }

    @Test
    @DisplayName("due 조회 후 지금 보내기로 갱신된 예약을 오래된 스냅샷으로 만료시키지 않는다")
    void givenRescheduledAfterDueRead_whenExpire_thenKeepsLatestSchedule() {
        Long id = addItem(roomId, now.minusMinutes(90), List.of(), "다시 예약", false);
        jdbc.update("update room_schedule_items set updated_at=? where id=?", now.minusMinutes(90), id);
        doAnswer(call -> {
            var snapshot = call.callRealMethod();
            scheduleService.sendNow(roomId, id, "admin:1");
            return snapshot;
        }).when(itemReader).findDue(any(), anyInt());

        assertThat(dispatcher.dispatchDue()).isZero();
        assertThat(item(id).get("status")).isEqualTo("SCHEDULED");
        doCallRealMethod().when(itemReader).findDue(any(), anyInt());
        assertThat(dispatcher.dispatchDue()).isEqualTo(1);
        assertThat(adminMessages(chatId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 편성의 실행 단계를 재호출해도 메시지는 한 번만 저장한다")
    void givenSentSchedule_whenSendAgain_thenNoDuplicate() {
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "중복 금지", false);
        assertThat(step.claim(id, now)).isTrue();
        var claimed = itemReader.findById(id).orElseThrow();
        step.send(claimed, chatId);
        assertThatThrownBy(() -> step.send(claimed, chatId)).isInstanceOf(RuntimeException.class);
        assertThat(adminMessages(chatId)).isEqualTo(1);
    }

    @Test
    @DisplayName("claim 후 방이 취소되면 메시지를 저장하지 않는다")
    void givenCanceledAfterClaim_whenSend_thenNoMessage() {
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "취소 후 발송 금지", false);
        assertThat(step.claim(id, now)).isTrue();
        var claimed = itemReader.findById(id).orElseThrow();
        jdbc.update("update rooms set status='CANCELLED' where id=?", roomId);

        assertThatThrownBy(() -> step.send(claimed, chatId)).isInstanceOf(RuntimeException.class);

        assertThat(adminMessages(chatId)).isZero();
        assertThat(item(id).get("status")).isEqualTo("SENDING");
    }

    @Test
    @DisplayName("인스턴스 2개가 동시에 돌아도 한 번만 보낸다: 운영 글 1건, SENT, 알림 행당 1건씩(멤버 3명)")
    void concurrentDispatchSendsOnce() throws Exception {
        jdbc.update("delete from notification_history");
        Long id = addItem(roomId, now.minusMinutes(1), List.of("MBTI", "INTO_NOW"), "서로의 MBTI 가 공개됐어요 👀", true);

        var executor = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    gate.await();
                    return dispatcher.dispatchDue();
                }));
            }
            gate.countDown();
            int total = 0;
            for (var f : futures) total += f.get(30, TimeUnit.SECONDS);
            assertThat(total).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(adminMessages(chatId)).isEqualTo(1);
        Map<String, Object> row = item(id);
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("sent_message_id")).isNotNull();
        assertThat(jdbc.queryForObject("select sender_id from chat_messages where id=?", Long.class,
                row.get("sent_message_id"))).isNull();
        assertThat(inbox("PROFILE_REVEAL")).isEqualTo(3);
        assertThat(inbox("ADMIN_MESSAGE")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select title from notification_history where type='PROFILE_REVEAL' limit 1",
                String.class)).isEqualTo("새 정보가 공개됐어요");
        assertThat(jdbc.queryForObject("select body from notification_history where type='ADMIN_MESSAGE' limit 1",
                String.class)).isEqualTo("방 채팅에 새 공지가 올라왔어요");
        // 참가자 CHAT 푸시에 안 섞인다
        assertThat(inbox("CHAT")).isZero();
    }

    @Test
    @DisplayName("더 이른 행에서 이미 공개된 항목은 PROFILE_REVEAL 에서 빠지고, 남는 게 없으면 공개 알림 없이 운영 글만 나간다")
    void revealPushSkipsAlreadyRevealedFields() {
        jdbc.update("delete from notification_history");
        Long first = addItem(roomId, now.minusMinutes(4), List.of("MBTI", "INTO_NOW"), null, false);
        Long second = addItem(roomId, now.minusMinutes(3), List.of("MBTI", "AGE"), null, false);
        Long third = addItem(roomId, now.minusMinutes(2), List.of("MBTI"), "MBTI 다시 확인해봐요", true);
        Long canceled = addItem(roomId, now.minusMinutes(5), List.of("DEPARTMENT"), null, false);
        scheduleService.cancel(roomId, canceled, "admin:1");
        Long department = addItem(roomId, now.minusMinutes(1), List.of("DEPARTMENT"), null, false);

        dispatcher.dispatchDue();

        assertThat(List.of(first, second, third, department)).allSatisfy(id -> assertThat(item(id).get("status")).isEqualTo("SENT"));
        assertThat(jdbc.queryForList("select title from notification_history where type='PROFILE_REVEAL' and user_id=?",
                String.class, alice))
                .containsExactlyInAnyOrder("새 정보가 공개됐어요", "영상 보고 몇 살 같았어요?", "문과일까, 이과일까?");
        assertThat(inbox("PROFILE_REVEAL")).isEqualTo(9);
        assertThat(inbox("ADMIN_MESSAGE")).isEqualTo(3);
        assertThat(adminMessages(chatId)).isEqualTo(1);
    }

    @Test
    @DisplayName("30분 지난 행은 EXPIRED, 방 종료 뒤 남은 행은 ROOM_ENDED, 시작 전 방의 행은 기다린다")
    void expiredEndedAndPending() {
        Long expired = addItem(roomId, now.minusMinutes(31), List.of(), "늦음", false);
        jdbc.update("update room_schedule_items set updated_at=? where id=?", now.minusMinutes(31), expired);
        Long endedRoom = room(now.minusHours(80), now.minusMinutes(20));
        Long ended = addItem(endedRoom, now.minusMinutes(10), List.of(), "끝난 방", false);
        Long pendingRoom = room(now.plusHours(1), now.plusHours(73));
        Long waiting = addItem(pendingRoom, now.minusHours(2), List.of(), "시작 전 08:00 행", false);

        dispatcher.dispatchDue();

        assertThat(item(expired).get("status")).isEqualTo("FAILED");
        assertThat(item(expired).get("fail_reason")).isEqualTo("EXPIRED");
        assertThat(item(ended).get("status")).isEqualTo("FAILED");
        assertThat(item(ended).get("fail_reason")).isEqualTo("ROOM_ENDED");
        assertThat(item(waiting).get("status")).isEqualTo("SCHEDULED");
        assertThat(adminMessages(chatId)).isZero();
    }

    @Test
    @DisplayName("만료 기준 = max(run_at, 방 시작, updated_at): 손 안 댄 옛 행만 EXPIRED, 방금 만들거나 고친 행은 과거 시각이어도 나간다")
    void expiryUsesUpdatedAt() {
        Long untouched = addItem(roomId, now.minusMinutes(90), List.of(), "옛 행", false);
        jdbc.update("update room_schedule_items set updated_at=? where id=?", now.minusMinutes(31), untouched);
        Long touchedRecently = addItem(roomId, now.minusMinutes(90), List.of(), "29분 전에 고친 행", false);
        jdbc.update("update room_schedule_items set updated_at=? where id=?", now.minusMinutes(29), touchedRecently);
        Long justCreated = addItem(roomId, now.minusHours(5), List.of(), "방금 만든 과거 시각 행", false);

        dispatcher.dispatchDue();

        assertThat(item(untouched).get("fail_reason")).isEqualTo("EXPIRED");
        assertThat(item(touchedRecently).get("status")).isEqualTo("SENT");
        assertThat(item(justCreated).get("status")).isEqualTo("SENT");
    }

    @Test
    @DisplayName("조회 뒤 어드민이 run_at 을 내일로 미루면 claim 이 실패하고, claim 뒤엔 다시 읽은 문구로 보낸다")
    void claimRechecksRunAtAndUsesFreshRow() {
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "옛 문구", false);
        var snapshot = itemReader.findDue(LocalDateTime.now(clock), 10);
        assertThat(snapshot).extracting(i -> i.id()).contains(id);

        scheduleService.update(roomId, id, new ScheduleItemCommand(now.plusDays(1), null, null, null, null, null), "admin:1");
        assertThat(step.claim(id, LocalDateTime.now(clock))).isFalse();
        assertThat(item(id).get("status")).isEqualTo("SCHEDULED");

        scheduleService.update(roomId, id, new ScheduleItemCommand(now.minusMinutes(1), null, "새 문구", null, null, null),
                "admin:1");
        dispatcher.dispatchDue();
        assertThat(jdbc.queryForObject("select content from chat_messages where id=?", String.class,
                item(id).get("sent_message_id"))).isEqualTo("새 문구");
    }

    @Test
    @DisplayName("지금 보내기(run_at=지금) 는 다음 틱에 나가고, SENDING 에 5분 넘게 멈춘 행은 STUCK")
    void sendNowAndStuck() {
        Long later = addItem(roomId, now.plusDays(2), List.of(), "조용하네요", false);
        Long stuck = addItem(roomId, now.plusDays(2), List.of(), "멈춤", false);
        jdbc.update("update room_schedule_items set status='SENDING', updated_at=? where id=?", now.minusMinutes(6), stuck);

        scheduleService.sendNow(roomId, later, "admin:1");
        dispatcher.dispatchDue();

        assertThat(item(later).get("status")).isEqualTo("SENT");
        assertThat(item(stuck).get("status")).isEqualTo("FAILED");
        assertThat(item(stuck).get("fail_reason")).isEqualTo("STUCK");
    }

    @Test
    @DisplayName("공개 전 value 는 null, 어드민이 공개 시각을 앞당기면 다음 조회부터 보인다(잡과 무관)")
    void revealFollowsSchedule() {
        Long ageRow = addItem(roomId, now.plusHours(3), List.of("AGE"), null, false);

        MemberProfileResponse before = memberProfiles.getMemberProfile(roomId, bobMemberId, alice);
        var ageBefore = before.fields().stream().filter(f -> f.key().equals("AGE")).findFirst().orElseThrow();
        assertThat(ageBefore.visible()).isFalse();
        assertThat(ageBefore.value()).isNull();
        assertThat(ageBefore.revealAt()).isCloseTo(now.plusHours(3), within(1, java.time.temporal.ChronoUnit.MILLIS));

        scheduleService.update(roomId, ageRow, new ScheduleItemCommand(now.minusMinutes(1), null, null, null, null, null),
                "admin:1");

        MemberProfileResponse after = memberProfiles.getMemberProfile(roomId, bobMemberId, alice);
        var ageAfter = after.fields().stream().filter(f -> f.key().equals("AGE")).findFirst().orElseThrow();
        assertThat(ageAfter.visible()).isTrue();
        assertThat(ageAfter.value()).isEqualTo(String.valueOf(now.getYear() - 2003));
        // 편성에 없는 항목은 공개되지 않는다
        assertThat(after.fields().stream().filter(f -> f.key().equals("MBTI")).findFirst().orElseThrow().visible())
                .isFalse();
    }

    @Test
    @DisplayName("시스템 유저 없이 운영 글을 저장·조회하고, 삭제는 '삭제된 메시지' + 방송 표시")
    void adminMessageShapeAndDelete() throws Exception {
        chats.sendMessage(chatId, alice, "안녕하세요");
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "하루방이 열렸어요 ☁️", false);
        dispatcher.dispatchDue();
        Long messageId = ((Number) item(id).get("sent_message_id")).longValue();
        var auth = new UsernamePasswordAuthenticationToken(bob, null, List.of());

        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].message_id").value(messageId))
                .andExpect(jsonPath("$.data.messages[0].sender_id").isEmpty())
                .andExpect(jsonPath("$.data.messages[0].content").value("하루방이 열렸어요 ☁️"))
                .andExpect(jsonPath("$.data.messages[0].message_type").value("TEXT"))
                .andExpect(jsonPath("$.data.messages[0].sender_type").value("SYSTEM"))
                .andExpect(jsonPath("$.data.messages[0].sender_name").value("하루"))
                .andExpect(jsonPath("$.data.messages[0].deleted").value(false))
                .andExpect(jsonPath("$.data.messages[1].message_type").value("TEXT"))
                .andExpect(jsonPath("$.data.messages[1].sender_name").isEmpty());
        // 운영용 계정/멤버를 추가하지 않는다.
        assertThat(jdbc.queryForObject("select count(*) from chat_room_members where chat_room_id=?",
                Long.class, chatId)).isEqualTo(3);
        // 운영 글에 답장하면 인용 이름은 '하루'
        chats.sendMessage(chatId, bob, "네!", messageId);

        scheduleService.deleteSentMessage(roomId, id);
        dispatcher.dispatchDue();

        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth)))
                .andExpect(jsonPath("$.data.messages[0].reply_to.sender_name").value("하루"))
                .andExpect(jsonPath("$.data.messages[0].reply_to.available").value(false))
                .andExpect(jsonPath("$.data.messages[1].content").value("삭제된 메시지"))
                .andExpect(jsonPath("$.data.messages[1].deleted").value(true))
                .andExpect(jsonPath("$.data.messages[1].sender_id").isEmpty());
        assertThat(jdbc.queryForObject("select delete_broadcast_at from chat_messages where id=?", LocalDateTime.class,
                messageId)).isNotNull();
        // 참가자 채팅 수에는 안 잡힌다: ChatMessageSentEvent 를 안 타므로 CHAT 알림도 운영 글 몫은 없다
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where chat_room_id=? and sender_type='USER'",
                Long.class, chatId)).isEqualTo(2);
    }
}
