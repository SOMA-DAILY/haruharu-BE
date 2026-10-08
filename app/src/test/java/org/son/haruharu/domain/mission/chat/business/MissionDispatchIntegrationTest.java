package org.son.haruharu.domain.mission.chat.business;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.mission.chat.business.command.MissionAnnouncementCommand;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;
import org.son.haruharu.domain.mission.chat.storage.MissionAnnouncementEntity;
import org.son.haruharu.domain.mission.chat.storage.MissionAnnouncementJpaRepository;
import org.son.haruharu.domain.mission.common.business.RoomMissionService;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.implement.MissionStatus;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.storage.RoomMissionEntity;
import org.son.haruharu.domain.mission.common.storage.RoomMissionJpaRepository;
import org.son.haruharu.domain.mission.common.implement.MissionDefinition;
import org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;
import org.son.haruharu.domain.mission.types.profilereveal.implement.MissionRevealField;
import org.son.haruharu.domain.mission.types.profilereveal.implement.ProfileRevealMissionDefinition;
import org.son.haruharu.domain.room.business.MemberProfileService;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.son.haruharu.infrastructure.stomp.StompDeliveryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 방 편성 발송 잡과 편성 기준 공개(#457) — PostgreSQL 에서 claim 경쟁, 상태 전이, 응답 모양 확인. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MissionDispatchIntegrationTest {
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
    @Autowired RoomMissionService missionService;
    @Autowired MissionAnnouncementService announcements;
    @Autowired RoomMissionJpaRepository missionRows;
    @Autowired RoomMissionReader missionReader;
    @Autowired MissionTypeRegistry types;
    @Autowired MissionAnnouncementJpaRepository announcementRows;
    @Autowired MissionDispatchService dispatcher;
    @Autowired MemberProfileService memberProfiles;
    @Autowired Clock clock;
    @Autowired ChatMessageReader messageReader;
    @Autowired org.son.haruharu.domain.profile.storage.UserProfileJpaRepository profiles;
    @Autowired org.son.haruharu.utils.JwtProvider jwtProvider;
    @Autowired tools.jackson.databind.json.JsonMapper jsonMapper;
    @Autowired org.son.haruharu.domain.chat.application.ChatStompController stompController;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @MockitoBean StompDeliveryPort delivery;
    @MockitoBean org.son.haruharu.infrastructure.s3.S3StoragePort storage;
    static final String IMAGE_KEY = "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-1080x720.jpg";
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader itemReader;
    @Autowired org.son.haruharu.domain.mission.chat.business.MissionDispatchStep step;

    LocalDateTime now;
    Long roomId, chatId, alice, bob, carol, bobMemberId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from mission_announcements");
        jdbc.update("delete from room_mission_instances");
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
        // 공개 항목이 있으면 공개 미션(문구는 안내가 가진다), 없으면 안내형 미션.
        MissionDefinition details = reveal.isEmpty() ? new AnnouncementMissionDefinition(content)
                : ProfileRevealMissionDefinition.from(reveal);
        var mission = missionRows.save(RoomMissionEntity.from(RoomMission.create(room, details.type(), details,
                runAt, null, "admin:1"), types)).toDomain(types);
        return announcementRows.save(MissionAnnouncementEntity.from(MissionAnnouncement.scheduled(mission,
                MissionAnnouncementType.START, runAt, content, push, null, null, "admin:1"))).toDomain().id();
    }

    Long missionId(Long id) { return itemReader.getById(id).roomMissionId(); }

    void reschedule(Long id, LocalDateTime at, String content) {
        announcements.update(roomId, missionId(id), id, new MissionAnnouncementCommand(
                MissionAnnouncementType.START, at, content, false, null, null), "admin:1");
    }

    Map<String, Object> item(Long id) {
        return jdbc.queryForMap("select status, fail_reason, sent_message_id, sent_at from mission_announcements where id=?", id);
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
            assertThat(message.path("message_type").asText()).isEqualTo("MISSION");
            assertThat(message.path("content").asText()).isEqualTo("HTTP 재조회");
            assertThat(message.path("mission").path("mission_id").asLong()).isEqualTo(missionId(id));
            assertThat(message.path("mission").path("announcement_id").asLong()).isEqualTo(id);
            assertThat(message.path("mission").path("interaction_type").asText()).isEqualTo("ANNOUNCEMENT");
        }
    }

    @Test
    @DisplayName("클라이언트가 SYSTEM과 다른 sender_id를 보내도 인증된 USER로만 저장한다")
    void givenForgedSenderPayload_whenStompSend_thenUsesAuthenticatedUser() {
        var request = jsonMapper.readValue("{\"content\":\"위조 시도\",\"sender_type\":\"SYSTEM\",\"sender_id\":999,\"message_type\":\"MISSION\",\"room_mission_id\":1,\"mission_announcement_id\":2}",
                org.son.haruharu.domain.chat.application.dto.SendMessageRequest.class);
        stompController.send(chatId, request, new org.son.haruharu.config.StompPrincipal(alice));
        var saved = messageReader.findLast(chatId, Set.of()).orElseThrow();
        assertThat(saved.senderType()).isEqualTo(org.son.haruharu.domain.chat.implement.ChatSenderType.USER);
        assertThat(saved.senderId()).isEqualTo(alice);
        assertThat(saved.messageType()).isEqualTo(org.son.haruharu.domain.chat.implement.ChatMessageType.TEXT);
        assertThat(saved.roomMissionId()).isNull();
        assertThat(saved.missionAnnouncementId()).isNull();
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
        announcements.deleteSentMessage(roomId, missionId(last), last);
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
        jdbc.update("update mission_announcements set updated_at=? where id=?", now.minusMinutes(90), id);
        doAnswer(call -> {
            var snapshot = call.callRealMethod();
            announcements.sendNow(roomId, missionId(id), id, "admin:1");
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
        var claimed = itemReader.getById(id);
        step.send(claimed, chatId);
        assertThatThrownBy(() -> step.send(claimed, chatId)).isInstanceOf(RuntimeException.class);
        assertThat(adminMessages(chatId)).isEqualTo(1);
    }

    @Test
    @DisplayName("claim 후 방이 취소되면 메시지를 저장하지 않는다")
    void givenCanceledAfterClaim_whenSend_thenNoMessage() {
        Long id = addItem(roomId, now.minusMinutes(1), List.of(), "취소 후 발송 금지", false);
        assertThat(step.claim(id, now)).isTrue();
        var claimed = itemReader.getById(id);
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
        missionService.cancel(roomId, missionId(canceled), "admin:1");
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
        jdbc.update("update mission_announcements set updated_at=? where id=?", now.minusMinutes(31), expired);
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
        jdbc.update("update mission_announcements set updated_at=? where id=?", now.minusMinutes(31), untouched);
        Long touchedRecently = addItem(roomId, now.minusMinutes(90), List.of(), "29분 전에 고친 행", false);
        jdbc.update("update mission_announcements set updated_at=? where id=?", now.minusMinutes(29), touchedRecently);
        Long justCreated = addItem(roomId, now.minusHours(5), List.of(), "방금 만든 과거 시각 행", false);

        dispatcher.dispatchDue();

        assertThat(item(untouched).get("fail_reason")).isEqualTo("EXPIRED");
        assertThat(item(touchedRecently).get("status")).isEqualTo("SENT");
        assertThat(item(justCreated).get("status")).isEqualTo("SENT");
    }

    @ParameterizedTest
    @ValueSource(ints = {123456000, 123456100, 123456900})
    @DisplayName("조회 뒤 어드민이 run_at 을 내일로 미루면 claim 이 실패하고, claim 뒤엔 다시 읽은 문구로 보낸다")
    void claimRechecksRunAtAndUsesFreshRow(int nanos) {
        LocalDateTime originalRunAt = now.minusMinutes(1).withNano(nanos);
        Long id = addItem(roomId, originalRunAt, List.of(), "옛 문구", false);
        var snapshot = itemReader.findDue(LocalDateTime.now(clock), 10);
        assertThat(snapshot).extracting(i -> i.id()).contains(id);
        // PostgreSQL timestamp(6)에서 반올림된 실제 예약 시각으로 복원한다.
        LocalDateTime persistedRunAt = snapshot.stream().filter(i -> i.id().equals(id))
                .findFirst().orElseThrow().runAt();

        reschedule(id, now.plusDays(1), "옛 문구");
        assertThat(step.claim(id, LocalDateTime.now(clock))).isFalse();
        assertThat(item(id).get("status")).isEqualTo("SCHEDULED");

        reschedule(id, persistedRunAt, "새 문구");
        dispatcher.dispatchDue();
        assertThat(jdbc.queryForObject("select content from chat_messages where id=?", String.class,
                item(id).get("sent_message_id"))).isEqualTo("새 문구");
    }

    @Test
    @DisplayName("지금 보내기(run_at=지금) 는 다음 틱에 나가고, SENDING 에 5분 넘게 멈춘 행은 STUCK")
    void sendNowAndStuck() {
        Long later = addItem(roomId, now.minusMinutes(1), List.of(), "조용하네요", false);
        reschedule(later, now.plusDays(2), "조용하네요");
        Long stuck = addItem(roomId, now.plusDays(2), List.of(), "멈춤", false);
        jdbc.update("update mission_announcements set status='SENDING', updated_at=? where id=?", now.minusMinutes(6), stuck);

        announcements.sendNow(roomId, missionId(later), later, "admin:1");
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

        missionService.update(roomId, missionId(ageRow), new RoomMissionCommand(MissionType.PROFILE_REVEAL,
                ProfileRevealMissionDefinition.from(List.of("AGE")), now.minusMinutes(1), null, null), "admin:1");

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
                .andExpect(jsonPath("$.data.messages[0].message_type").value("MISSION"))
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

        announcements.deleteSentMessage(roomId, missionId(id), id);
        dispatcher.dispatchDue();

        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth)))
                .andExpect(jsonPath("$.data.messages[0].reply_to.sender_name").value("하루"))
                .andExpect(jsonPath("$.data.messages[0].reply_to.available").value(false))
                .andExpect(jsonPath("$.data.messages[1].content").value("삭제된 메시지"))
                .andExpect(jsonPath("$.data.messages[1].deleted").value(true))
                .andExpect(jsonPath("$.data.messages[1].mission").isEmpty())
                .andExpect(jsonPath("$.data.messages[1].sender_id").isEmpty());
        assertThat(jdbc.queryForObject("select delete_broadcast_at from chat_messages where id=?", LocalDateTime.class,
                messageId)).isNotNull();
        // 참가자 채팅 수에는 안 잡힌다: ChatMessageSentEvent 를 안 타므로 CHAT 알림도 운영 글 몫은 없다
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where chat_room_id=? and sender_type='USER'",
                Long.class, chatId)).isEqualTo(2);
    }

    java.net.http.HttpResponse<String> http(String path, Long viewer) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + path));
        if (viewer != null) request.header("Authorization", "Bearer " + jwtProvider.generateAccessToken(viewer));
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            return client.send(request.GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    @DisplayName("실제 HTTP: 채팅 없는 미션도 시작 이후 참가자만 조회하며 미래·다른 방 내용은 숨긴다")
    void givenMissionsWithoutChat_whenParticipantHttpRead_thenEnforcesMembershipVisibilityAndPagination() throws Exception {
        Long standaloneRoom = room(now.minusHours(1), now.plusHours(71));
        members.save(RoomMemberEntity.from(RoomMember.of(standaloneRoom, alice, "MALE", LocalDate.of(2003,1,1), now)));
        long future = missionService.create(standaloneRoom, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("미래 비밀"), now.plusHours(1), null, null), "admin:1").missionId();
        long open = missionService.create(standaloneRoom, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("시작된 질문"), now.minusMinutes(1), null, null), "admin:1").missionId();
        long closed = missionService.create(standaloneRoom, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("마감된 질문"), now.minusMinutes(2), now.minusMinutes(1), null), "admin:1").missionId();
        String path = "/api/v1/rooms/"+standaloneRoom+"/mission-instances";
        long messagesBefore = jdbc.queryForObject("select count(*) from chat_messages",Long.class);
        assertThat(http(path+"/"+open, null).statusCode()).isEqualTo(401);
        assertThat(http(path+"/"+open, bob).statusCode()).isEqualTo(403);
        assertThat(http(path+"/"+future, alice).statusCode()).isEqualTo(404);
        assertThat(http("/api/v1/rooms/"+roomId+"/mission-instances/"+open, alice).statusCode()).isEqualTo(404);
        var visible = http(path+"/"+open, alice);
        assertThat(visible.statusCode()).isEqualTo(200);
        var data = jsonMapper.readTree(visible.body()).path("data");
        assertThat(data.path("status").asText()).isEqualTo("OPEN");
        assertThat(data.path("details").path("content").asText()).isEqualTo("시작된 질문");
        assertThat(data.has("content")).isFalse();
        assertThat(data.path("requires_response").asBoolean()).isFalse();
        assertThat(data.path("can_respond").asBoolean()).isFalse();
        assertThat(visible.body()).doesNotContain("created_by", "push_title", "未来", "미래 비밀");
        var lookup = http(path+"/lookup?ids="+open+","+future+","+closed+",999999",alice);
        assertThat(lookup.statusCode()).isEqualTo(200);
        assertThat(jsonMapper.readTree(lookup.body()).path("data").size()).isEqualTo(2);
        assertThat(lookup.body()).contains("CLOSED").doesNotContain("미래 비밀");
        var page=jsonMapper.readTree(http(path+"?size=1",alice).body()).path("data");
        assertThat(page.path("missions").size()).isEqualTo(1);
        assertThat(page.path("next_cursor").asLong()).isEqualTo(open);
        assertThat(jsonMapper.readTree(http(path+"?size=1&after="+open,alice).body()).path("data").path("next_cursor").isNull()).isTrue();
        assertThat(http(path+"?size=101",alice).statusCode()).isEqualTo(400);
        assertThat(http(path+"/lookup?ids="+open,bob).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from chat_messages",Long.class)).isEqualTo(messagesBefore);
        assertThat(jdbc.queryForObject("select count(*) from chat_rooms where room_id=?",Long.class,standaloneRoom)).isZero();
    }

    @Test
    void givenDefinitionOrAnnouncementStorageFailure_whenCreate_thenRollsBackWholeMission() {
        jdbc.execute("ALTER TABLE room_mission_instances ADD CONSTRAINT test_details_write CHECK(details->>'content' <> '저장 거부')");
        jdbc.execute("ALTER TABLE mission_announcements ADD CONSTRAINT test_plan_write CHECK(content <> '게시 거부')");
        try {
            var start = now.plusHours(1);
            assertThatThrownBy(() -> missionService.create(roomId, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                    new AnnouncementMissionDefinition("저장 거부"), start, null, null), "admin:1"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(missionRows.count()).isZero();
            assertThatThrownBy(() -> missionService.create(roomId, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                    new AnnouncementMissionDefinition("정상 정의"), start, null,
                    new MissionAnnouncementCommand(MissionAnnouncementType.START, start, "게시 거부", false, null, null)), "admin:1"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(missionRows.count()).isZero();
            assertThat(announcementRows.count()).isZero();
        } finally {
            jdbc.execute("ALTER TABLE room_mission_instances DROP CONSTRAINT test_details_write");
            jdbc.execute("ALTER TABLE mission_announcements DROP CONSTRAINT test_plan_write");
        }
    }

    @Test
    void givenDefinitionStorageFailure_whenUpdating_thenPreservesScheduleDefinitionAndAudit() {
        var start = now.plusHours(1);
        long id = missionService.create(roomId, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("원본"), start, null, null), "admin:1").missionId();
        var before = jdbc.queryForMap("select * from room_mission_instances where id=?", id);
        assertThat(jdbc.queryForObject("select details::text from room_mission_instances where id=?", String.class, id))
                .isEqualTo("{\"content\": \"원본\"}");
        jdbc.execute("ALTER TABLE room_mission_instances ADD CONSTRAINT test_details_write CHECK(details->>'content' <> '저장 거부')");
        try {
            assertThatThrownBy(() -> missionService.update(roomId, id, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                    new AnnouncementMissionDefinition("저장 거부"), start.plusHours(1), null, null), "admin:2"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForMap("select * from room_mission_instances where id=?", id)).isEqualTo(before);
        } finally {
            jdbc.execute("ALTER TABLE room_mission_instances DROP CONSTRAINT test_details_write");
        }
        var updated = missionService.update(roomId, id, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("수정"), start.plusHours(1), null, null), "admin:2");
        assertThat(updated.details()).isEqualTo(new AnnouncementMissionDefinition("수정"));
        var after = jdbc.queryForMap("select * from room_mission_instances where id=?", id);
        assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));
        assertThat(after.get("updated_by")).isEqualTo("admin:2");
        assertThat(after.get("opens_at")).isNotEqualTo(before.get("opens_at"));
    }

    @Test
    void givenRevealMission_whenStored_thenKeepsFieldsAsJsonArrayAndRejectsNonObjectDetails() {
        long id = missionService.create(roomId, new RoomMissionCommand(MissionType.PROFILE_REVEAL,
                ProfileRevealMissionDefinition.from(List.of("AGE", "MBTI")), now.plusHours(1), null, null), "admin:1").missionId();
        assertThat(jdbc.queryForObject("select details::text from room_mission_instances where id=?", String.class, id))
                .isEqualTo("{\"reveal_fields\": [\"MBTI\", \"AGE\"]}");
        assertThat(jdbc.queryForObject("select data_type from information_schema.columns "
                + "where table_name='room_mission_instances' and column_name='details'", String.class)).isEqualTo("jsonb");
        assertThat(missionService.get(roomId, id).details())
                .isEqualTo(ProfileRevealMissionDefinition.from(List.of("MBTI", "AGE")));
        assertThatThrownBy(() -> jdbc.update("update room_mission_instances set details=null where id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void givenStartedOrAnnouncedRevealMission_whenUpdatingDefinition_thenPreservesSeenAndPlannedRules() {
        long opened = addItem(roomId, now.minusMinutes(1), List.of("AGE"), "공개한 규칙", false);
        var replacement = ProfileRevealMissionDefinition.from(List.of("DEPARTMENT"));
        assertThatThrownBy(() -> missionService.update(roomId, missionId(opened), new RoomMissionCommand(
                MissionType.PROFILE_REVEAL, replacement, now.plusHours(2), null, null), "admin:2"))
                .isInstanceOf(org.son.haruharu.domain.mission.common.business.exception.MissionNotEditableException.class);
        assertThat(missionReader.getById(missionId(opened)).details())
                .isEqualTo(ProfileRevealMissionDefinition.from(List.of("AGE")));
        // 시작을 기존 예약 안내 뒤로 미루면 안내가 미션보다 먼저 나가게 된다.
        long future = addItem(roomId, now.plusHours(1), List.of("MBTI"), null, false);
        assertThatThrownBy(() -> missionService.update(roomId, missionId(future), new RoomMissionCommand(
                MissionType.PROFILE_REVEAL, replacement, now.plusHours(2), null, null), "admin:2"))
                .isInstanceOf(org.son.haruharu.domain.mission.common.business.exception.MissionAnnouncementConflictException.class);
        assertThat(missionReader.getById(missionId(future)).details())
                .isEqualTo(new ProfileRevealMissionDefinition(List.of(MissionRevealField.MBTI)));
        // 유형은 바꿀 수 없고, 공개 미션에서 공개 항목을 비울 수 없다.
        assertThatThrownBy(() -> missionService.update(roomId, missionId(future), new RoomMissionCommand(
                MissionType.ANNOUNCEMENT, new AnnouncementMissionDefinition("안내로 변경"), now.plusHours(1), null, null), "admin:2"))
                .isInstanceOf(org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException.class);
        assertThatThrownBy(() -> missionService.update(roomId, missionId(future), new RoomMissionCommand(
                MissionType.PROFILE_REVEAL, ProfileRevealMissionDefinition.from(List.of()), now.plusHours(1), null, null), "admin:2"))
                .isInstanceOf(org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException.class);
    }

    @Test
    void givenSoftDeletedOrOtherTypeMission_whenReadingRevealMissions_thenExcludesThem() {
        long deleted = missionId(addItem(roomId, now.plusHours(1), List.of("AGE"), null, false));
        long retained = missionId(addItem(roomId, now.plusHours(1), List.of("DEPARTMENT"), null, false));
        addItem(roomId, now.plusHours(1), List.of(), "공개 없는 안내", false);
        jdbc.update("update room_mission_instances set deleted_at=now() where id=?", deleted);
        assertThat(missionReader.findByRoomIdAndType(roomId, MissionType.PROFILE_REVEAL))
                .extracting(RoomMission::id).containsExactly(retained);
        var fields = memberProfiles.getMemberProfile(roomId, bobMemberId, alice).fields();
        assertThat(fields.stream().filter(f -> f.key().equals("DEPARTMENT")).findFirst().orElseThrow().revealAt()).isNotNull();
        assertThat(fields.stream().filter(f -> f.key().equals("AGE")).findFirst().orElseThrow().revealAt()).isNull();
    }

    @Test
    void givenOneMissionWithMultipleAnnouncements_whenSendDeleteCancel_thenKeepSeparateLifecycles() {
        Long start = addItem(roomId, now.minusMinutes(1), List.of(), "첫 게시 스냅샷", false);
        long mission = missionId(start);
        var reminder = announcements.add(roomId, mission, new MissionAnnouncementCommand(MissionAnnouncementType.REMINDER,
                now, "추가 안내", false, null, null), "admin:1");
        var pending = announcements.add(roomId, mission, new MissionAnnouncementCommand(MissionAnnouncementType.RESULT,
                now.plusHours(1), "결과 안내", false, null, null), "admin:1");
        assertThat(dispatcher.dispatchDue()).isEqualTo(2);
        var messages = chats.getMessages(chatId, alice, null, 20).messages();
        assertThat(messages).hasSize(2).allSatisfy(message -> {
            assertThat(message.messageType()).isEqualTo("MISSION");
            assertThat(message.mission().missionId()).isEqualTo(mission);
        });
        assertThat(messages).extracting(m -> m.mission().announcementId()).containsExactly(reminder.announcementId(),start);
        assertThat(missionService.get(roomId, mission).status()).isEqualTo(MissionStatus.OPEN);
        announcements.deleteSentMessage(roomId, mission, start);
        assertThat(missionService.get(roomId, mission).status()).isEqualTo(MissionStatus.OPEN);
        missionService.cancel(roomId, mission, "admin:2");
        assertThat(item(pending.announcementId()).get("status")).isEqualTo("CANCELED");
        assertThat(item(reminder.announcementId()).get("status")).isEqualTo("SENT");
        assertThat(adminMessages(chatId)).isEqualTo(2);
        assertThat(missionService.getForParticipant(roomId,mission,alice).status()).isEqualTo(MissionStatus.CANCELED);
        assertThat(dispatcher.dispatchDue()).isZero();
    }

    @Test
    void givenClaimedAnnouncement_whenMissionCanceledOrWrongChat_thenNoRecordOrBroadcast() {
        Long id=addItem(roomId,now.minusMinutes(1),List.of(),"미션 취소",false);
        assertThat(step.claim(id,now)).isTrue();
        var claimed=itemReader.getById(id);
        missionService.cancel(roomId,missionId(id),"admin:2");
        assertThatThrownBy(()->step.send(claimed,chatId)).hasMessageContaining("실행할 수 없습니다");
        Long second=addItem(roomId,now.minusMinutes(1),List.of(),"잘못된 방",false);
        assertThat(step.claim(second,now)).isTrue();
        Long otherRoom=room(now.minusHours(1),now.plusHours(71));
        chats.createGroupRoom(otherRoom);
        Long otherChat=jdbc.queryForObject("select id from chat_rooms where room_id=?",Long.class,otherRoom);
        assertThatThrownBy(()->step.send(itemReader.getById(second),otherChat)).hasMessageContaining("실행할 수 없습니다");
        assertThat(adminMessages(chatId)).isZero();
        assertThat(adminMessages(otherChat)).isZero();
        verifyNoInteractions(delivery);
    }

    @Test
    void givenFailedOrCanceledRevealMission_whenReadProfile_thenIndependentOfDeliveryAndNoLegacyFallback() {
        jdbc.update("update rooms set starts_at=? where id=?",now.minusHours(60),roomId);
        Long id=addItem(roomId,now.minusMinutes(1),List.of("AGE"),null,false);
        jdbc.update("update mission_announcements set status='FAILED' where id=?",id);
        assertThat(memberProfiles.getMemberProfile(roomId,bobMemberId,alice).fields().stream()
                .filter(f->f.key().equals("AGE")).findFirst().orElseThrow().visible()).isTrue();
        missionService.cancel(roomId,missionId(id),"admin:1");
        assertThat(memberProfiles.getMemberProfile(roomId,bobMemberId,alice).fields().stream()
                .filter(f->f.key().equals("AGE")).findFirst().orElseThrow().visible()).isFalse();
    }

    @Test
    void givenMissionWithImage_whenDispatchedAndRead_thenBubbleCarriesSignedImageEverywhere() throws Exception {
        when(storage.head(IMAGE_KEY)).thenReturn(new org.son.haruharu.infrastructure.s3.S3StoragePort.ObjectMetadata(10, "image/jpeg"));
        when(storage.publicBaseUrl()).thenReturn("https://bucket.example/");
        when(storage.createPresignedImageGetUrl(anyString())).thenAnswer(i -> i.getArgument(0) + "?signed");
        String url = "https://bucket.example/" + IMAGE_KEY + "?signed";
        var start = now.minusMinutes(1);
        long withImage = missionService.create(roomId, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("사진 있는 안내"), IMAGE_KEY, start, null,
                new MissionAnnouncementCommand(MissionAnnouncementType.START, start, "사진을 확인해 주세요", false, null, null)),
                "admin:1").missionId();
        long reveal = missionService.create(roomId, new RoomMissionCommand(MissionType.PROFILE_REVEAL,
                ProfileRevealMissionDefinition.from(List.of("MBTI")), IMAGE_KEY, start, null,
                new MissionAnnouncementCommand(MissionAnnouncementType.START, start, "MBTI 공개", false, null, null)),
                "admin:1").missionId();
        long plain = missionId(addItem(roomId, start, List.of(), "사진 없는 안내", false));
        // 저장은 details JSON 의 예약 키 image. 유형별 값과 한 칸에 있다.
        assertThat(jdbc.queryForObject("select details::text from room_mission_instances where id=?", String.class, withImage))
                .isEqualTo("{\"image\": {\"key\": \"" + IMAGE_KEY + "\", \"width\": 1080, \"height\": 720}, \"content\": \"사진 있는 안내\"}");

        assertThat(dispatcher.dispatchDue()).isEqualTo(3);

        // 실시간 방송: 추가 조회 없이 그릴 수 있게 서명 주소와 크기를 함께 보낸다.
        var broadcasts = org.mockito.ArgumentCaptor.forClass(ChatMessageBroadcast.class);
        verify(delivery, times(3)).sendToTopic(anyString(), broadcasts.capture(), anyString());
        var byMission = new HashMap<Long, ChatMessageBroadcast>();
        broadcasts.getAllValues().forEach(b -> byMission.put(b.mission().missionId(), b));
        assertThat(byMission.get(withImage).mission().image())
                .isEqualTo(new org.son.haruharu.domain.chat.implement.ChatMissionImage(IMAGE_KEY, url, 1080, 720));
        assertThat(byMission.get(reveal).mission().image().key()).isEqualTo(IMAGE_KEY);
        assertThat(byMission.get(plain).mission().image()).isNull();

        // 기록 조회: 같은 모양. 삭제한 안내에는 미션도 사진도 내려가지 않는다.
        var messages = chats.getMessages(chatId, alice, null, 20).messages();
        assertThat(messages).filteredOn(m -> m.mission().missionId().equals(withImage)).singleElement()
                .satisfies(m -> assertThat(m.mission().image().url()).isEqualTo(url));
        assertThat(messages).filteredOn(m -> m.mission().missionId().equals(plain)).singleElement()
                .satisfies(m -> assertThat(m.mission().image()).isNull());
        long announcementId = messages.stream().filter(m -> m.mission().missionId().equals(withImage)).findFirst()
                .orElseThrow().mission().announcementId();
        announcements.deleteSentMessage(roomId, withImage, announcementId);
        assertThat(chats.getMessages(chatId, alice, null, 20).messages()).filteredOn(m -> m.deleted()).singleElement()
                .satisfies(m -> assertThat(m.mission()).isNull());

        // 미션 조회: 주소가 만료되면 여기서 새로 받는다.
        var looked = missionService.findForParticipant(roomId, alice, List.of(withImage, plain));
        assertThat(looked).filteredOn(m -> m.missionId().equals(withImage)).singleElement()
                .satisfies(m -> assertThat(m.image()).isEqualTo(
                        new org.son.haruharu.domain.mission.common.business.dto.MissionImageResponse(IMAGE_KEY, url, 1080, 720)));
        assertThat(looked).filteredOn(m -> m.missionId().equals(plain)).singleElement()
                .satisfies(m -> assertThat(m.image()).isNull());
        mvc.perform(get("/api/v1/rooms/" + roomId + "/mission-instances/" + withImage)
                        .with(authentication(new UsernamePasswordAuthenticationToken(alice, null, List.of()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.image.url").value(url))
                .andExpect(jsonPath("$.data.image.width").value(1080))
                .andExpect(jsonPath("$.data.details.image").doesNotExist());
    }
}
