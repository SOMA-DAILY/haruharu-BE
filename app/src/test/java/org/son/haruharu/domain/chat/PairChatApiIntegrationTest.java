package org.son.haruharu.domain.chat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.business.PairChatService;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.domain.chat.business.exception.ChatRoomClosedException;
import org.son.haruharu.domain.chat.business.exception.PairChatClosedException;
import org.son.haruharu.domain.mission.common.business.RoomMissionService;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.types.pairchat.implement.PairChatMissionDefinition;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.RoomStatusTransitionService;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 운영자가 정한 시간에만 열리는 1:1 대화(#540) — PostgreSQL 에서 API·유일 제약·동시 열기·방 종료 확인. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@RecordApplicationEvents
class PairChatApiIntegrationTest {
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
    @Autowired UserProfileJpaRepository profiles;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired RoomMissionService missions;
    @Autowired RoomService roomService;
    @Autowired ChatService chats;
    @Autowired PairChatService pairChats;
    @Autowired org.son.haruharu.domain.chat.implement.ChatRoomReader chatRoomReader;
    @Autowired RoomStatusTransitionService transitions;
    @Autowired ApplicationEvents events;
    @Autowired Clock clock;

    Long roomId, me, partner, blocked, outsider;
    Long myMemberId, partnerMemberId, blockedMemberId;
    LocalDateTime now;

    @BeforeEach
    void setup() {
        now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(5), now.plusHours(40),
                now.plusHours(41), now.plusHours(42), now.plusHours(43), 3))).toDomain().id();
        me = user("김실명");
        partner = user("박실명");
        blocked = user("최실명");
        outsider = user("이실명");
        myMemberId = member(me, "하늘");
        partnerMemberId = member(partner, "서연");
        blockedMemberId = member(blocked, "도윤");
    }

    Long user(String nickname) {
        Long id = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        profiles.save(UserProfileEntity.from(new UserProfile(null, id, null, null, null, null, nickname,
                null, null, null)));
        return id;
    }

    Long member(Long userId, String pseudonym) {
        return members.save(RoomMemberEntity.from(new RoomMember(null, roomId, userId, "FEMALE", pseudonym, null, now)))
                .toDomain().id();
    }

    Long window(LocalDateTime opensAt, LocalDateTime closesAt) {
        return missions.create(roomId, new RoomMissionCommand(MissionType.PAIR_CHAT, new PairChatMissionDefinition(),
                opensAt, closesAt, null), "admin:1").missionId();
    }

    UsernamePasswordAuthenticationToken auth(Long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of());
    }

    long open(Long user, Long target) throws Exception {
        String body = mvc.perform(post("/api/v1/rooms/{roomId}/pair-chats", roomId).with(authentication(auth(user)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target_room_member_id\":" + target + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.data.chat_room_id", Long.class);
    }

    @Test
    @DisplayName("대화 시간 중: 상태 조회 → 열기(멱등, 양쪽 같은 방) → 가명 상세 → 보내기 → include_pair 목록에만 보임")
    void givenOpenWindow_whenOpenSendAndList_thenPairChatFlow() throws Exception {
        LocalDateTime start = now.minusMinutes(10), end = now.plusMinutes(50);
        window(start, end);
        window(now.plusHours(24), now.plusHours(26));

        mvc.perform(get("/api/v1/rooms/{roomId}/pair-chat-window", roomId).with(authentication(auth(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("OPEN"))
                .andExpect(jsonPath("$.data.opens_day").value(1))
                .andExpect(jsonPath("$.data.closes_at").value(end.toString().length() == 16 ? end + ":00" : end.toString()));
        mvc.perform(get("/api/v1/rooms/{roomId}/pair-chat-window", roomId).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());

        long chatId = open(me, partnerMemberId);
        assertThat(open(me, partnerMemberId)).isEqualTo(chatId);
        assertThat(open(partner, myMemberId)).isEqualTo(chatId);
        assertThat(jdbc.queryForObject("select count(*) from chat_rooms where type='PAIR' and room_id=?", Long.class, roomId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select room_id from chat_rooms where id=?", Long.class, chatId)).isEqualTo(roomId);

        mvc.perform(get("/api/v1/chat-rooms/{id}", chatId).with(authentication(auth(me))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("PAIR"))
                .andExpect(jsonPath("$.data.partner.nickname").value("서연"))
                .andExpect(jsonPath("$.data.room_id").value(roomId))
                .andExpect(jsonPath("$.data.pair_chat.open").value(true))
                .andExpect(jsonPath("$.data.pair_chat.next_opens_at").isEmpty());

        // 열기만 하고 안 보낸 방은 목록에 없다.
        mvc.perform(get("/api/v1/chat-rooms").param("include_pair", "true").with(authentication(auth(partner))))
                .andExpect(jsonPath("$.data.chat_rooms[?(@.type=='PAIR')]").isEmpty());

        chats.sendMessage(chatId, me, "카페 영상 봤어요!");

        mvc.perform(get("/api/v1/chat-rooms").param("include_pair", "true").with(authentication(auth(partner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chat_rooms[0].type").value("PAIR"))
                .andExpect(jsonPath("$.data.chat_rooms[0].partner.nickname").value("하늘"))
                .andExpect(jsonPath("$.data.chat_rooms[0].last_message").value("카페 영상 봤어요!"))
                .andExpect(jsonPath("$.data.chat_rooms[0].unread_count").value(1))
                .andExpect(jsonPath("$.data.chat_rooms[0].room_id").value(roomId));
        // 기본 목록(구버전 앱)에는 PAIR 가 없다.
        mvc.perform(get("/api/v1/chat-rooms").with(authentication(auth(partner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chat_rooms[?(@.type=='PAIR')]").isEmpty());
        // 받는 쪽 메시지의 답장 인용·표시 이름도 가명이다.
        mvc.perform(get("/api/v1/chat-rooms/{id}/messages", chatId).with(authentication(auth(partner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content").value("카페 영상 봤어요!"));
    }

    @Test
    @DisplayName("열기 거절: 대화 시간 밖 409, 나 자신 400, 다른 방 참가자 아님 403")
    void givenInvalidOpen_whenOpen_thenRejected() throws Exception {
        window(now.plusHours(2), now.plusHours(4));

        mvc.perform(get("/api/v1/rooms/{roomId}/pair-chat-window", roomId).with(authentication(auth(me))))
                .andExpect(jsonPath("$.data.state").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.opens_day").isNumber());
        mvc.perform(post("/api/v1/rooms/{roomId}/pair-chats", roomId).with(authentication(auth(me)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target_room_member_id\":" + partnerMemberId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAIR_CHAT_NOT_OPEN"));
        mvc.perform(post("/api/v1/rooms/{roomId}/pair-chats", roomId).with(authentication(auth(me)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target_room_member_id\":" + myMemberId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAIR_CHAT_INVALID_TARGET"));
        mvc.perform(post("/api/v1/rooms/{roomId}/pair-chats", roomId).with(authentication(auth(outsider)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target_room_member_id\":" + partnerMemberId + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/rooms/{roomId}/pair-chats", roomId).with(authentication(auth(me)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("대화 시간이 취소되면 보내기는 PAIR_CHAT_CLOSED, 상세는 닫힘과 다음 시작을 알려 준다")
    void givenWindowCanceled_whenSendAndDetail_thenClosedWithNextOpen() throws Exception {
        Long current = window(now.minusMinutes(10), now.plusMinutes(50));
        LocalDateTime nextStart = now.plusHours(24);
        window(nextStart, nextStart.plusHours(2));
        long chatId = open(me, partnerMemberId);
        chats.sendMessage(chatId, me, "안녕하세요");

        missions.cancel(roomId, current, "admin:1");

        assertThatThrownBy(() -> chats.sendMessage(chatId, partner, "답장"))
                .isInstanceOf(PairChatClosedException.class);
        mvc.perform(get("/api/v1/chat-rooms/{id}", chatId).with(authentication(auth(partner))))
                .andExpect(jsonPath("$.data.pair_chat.open").value(false))
                .andExpect(jsonPath("$.data.pair_chat.closes_at").isEmpty())
                .andExpect(jsonPath("$.data.pair_chat.next_opens_at").isNotEmpty());
        // 지난 대화는 읽을 수 있다.
        mvc.perform(get("/api/v1/chat-rooms/{id}/messages", chatId).with(authentication(auth(partner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].content").value("안녕하세요"));
    }

    @Test
    @DisplayName("방이 끝나면 PAIR 도 닫힌다: 보내기 CHAT_ROOM_CLOSED, include_pair 목록에서 빠진다")
    void givenRoomCanceled_whenSendOrList_thenPairClosedAndHidden() throws Exception {
        window(now.minusMinutes(10), now.plusMinutes(50));
        long chatId = open(me, partnerMemberId);
        chats.sendMessage(chatId, me, "안녕하세요");

        roomService.cancel(roomId);

        assertThat(jdbc.queryForObject("select close_at from chat_rooms where id=?", LocalDateTime.class, chatId))
                .isNotNull();
        assertClosedEventTo(chatId, me, partner);
        assertThatThrownBy(() -> chats.sendMessage(chatId, me, "끝난 뒤"))
                .isInstanceOf(ChatRoomClosedException.class);
        mvc.perform(get("/api/v1/chat-rooms").param("include_pair", "true").with(authentication(auth(partner))))
                .andExpect(jsonPath("$.data.chat_rooms[?(@.type=='PAIR')]").isEmpty());
    }

    @Test
    @DisplayName("같은 방에 PAIR 가 있어도 room_id 로 찾는 GROUP 조회·생성·멤버 추가는 GROUP 만 다룬다")
    void givenPairInRoom_whenGroupLookupCreateAndJoin_thenOnlyGroupTouched() throws Exception {
        window(now.minusMinutes(10), now.plusMinutes(50));
        long pairId = open(me, partnerMemberId);

        // PAIR 만 있으면 GROUP 은 없는 것이다(있다고 보면 방 시작 때 GROUP 을 만들지 않는다).
        assertThat(chatRoomReader.existsByRoomId(roomId)).isFalse();
        assertThat(chatRoomReader.findByRoomId(roomId)).isEmpty();

        chats.createGroupRoom(roomId);
        chats.createGroupRoom(roomId); // 시작 이벤트 재발행에도 GROUP 은 하나
        Long groupId = jdbc.queryForObject("select id from chat_rooms where type='GROUP' and room_id=?", Long.class, roomId);

        assertThat(chatRoomReader.existsByRoomId(roomId)).isTrue();
        assertThat(chatRoomReader.getByRoomId(roomId).id()).isEqualTo(groupId);
        assertThat(chatRoomReader.getIdByRoomId(roomId)).isEqualTo(groupId);
        assertThat(chats.getGroupRoomBySourceRoom(roomId, me).chatRoomId()).isEqualTo(groupId);

        Long newcomer = user("정실명");
        member(newcomer, "민준");
        chats.addGroupRoomMember(roomId, newcomer, now);
        assertThat(jdbc.queryForObject("select count(*) from chat_room_members where chat_room_id=? and user_id=?",
                Long.class, groupId, newcomer)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from chat_room_members where chat_room_id=?", Long.class, pairId))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("방이 끝 시각이 되어 상태 전이로 끝나도 PAIR 가 닫히고, 두 사람에게 한 번 종료를 알린다")
    void givenRoomEndsByTime_whenTransition_thenPairClosed() throws Exception {
        window(now.minusMinutes(10), now.plusMinutes(50));
        long chatId = open(me, partnerMemberId);
        chats.sendMessage(chatId, me, "안녕하세요");
        Room room = rooms.findById(roomId).orElseThrow().toDomain();
        transitions.transition(room, now); // 방 시작(PENDING → ACTIVE)

        assertThat(transitions.transition(room, room.endsAt())).isTrue();

        assertThat(rooms.findById(roomId).orElseThrow().toDomain().status()).isEqualTo(RoomStatus.ENDED);
        assertThat(jdbc.queryForObject("select close_at from chat_rooms where id=?", LocalDateTime.class, chatId))
                .isNotNull();
        assertClosedEventTo(chatId, me, partner);
        assertThatThrownBy(() -> chats.sendMessage(chatId, partner, "끝난 뒤"))
                .isInstanceOf(ChatRoomClosedException.class);
    }

    @Test
    @DisplayName("상대 프로필 카드(매칭 상대 전용)는 PAIR 방이면 404 CHAT_ROOM_NOT_FOUND, 참여자가 아니면 기존대로 403")
    void givenPairRoom_whenGetPartnerCard_thenNotFound() throws Exception {
        window(now.minusMinutes(10), now.plusMinutes(50));
        long chatId = open(me, partnerMemberId);

        mvc.perform(get("/api/v1/chat-rooms/{id}/partner", chatId).with(authentication(auth(me))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_ROOM_NOT_FOUND"));
        mvc.perform(get("/api/v1/chat-rooms/{id}/partner", chatId).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_NOT_MEMBER"));
    }

    /** 닫힌 PAIR 방의 종료 알림(구독 종료·개인 알림 대상)은 한 번, 두 사람 모두에게 간다. */
    void assertClosedEventTo(long chatId, Long... userIds) {
        assertThat(events.stream(ChatRoomClosedEvent.class).filter(event -> event.chatRoomId().equals(chatId)))
                .singleElement()
                .satisfies(event -> assertThat(event.memberUserIds()).containsExactlyInAnyOrder(userIds));
    }

    @Test
    @DisplayName("차단 관계면 열 수 없다(403 CHAT_PARTNER_BLOCKED)")
    void givenBlocked_whenOpen_thenForbidden() throws Exception {
        window(now.minusMinutes(10), now.plusMinutes(50));
        jdbc.update("insert into user_blocks (blocker_user_id, blocked_user_id, created_at, updated_at) values (?, ?, now(), now())",
                blocked, me);

        mvc.perform(post("/api/v1/rooms/{roomId}/pair-chats", roomId).with(authentication(auth(me)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"target_room_member_id\":" + blockedMemberId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_PARTNER_BLOCKED"));
    }

    @Test
    @DisplayName("두 사람이 동시에 서로를 열어도 PAIR 채팅방은 하나다")
    void givenConcurrentOpens_whenBothOpen_thenSinglePairRoom() throws Exception {
        window(now.minusMinutes(10), now.plusMinutes(50));
        var executor = Executors.newFixedThreadPool(6);
        var gate = new CountDownLatch(1);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                futures.add(executor.submit(() -> { gate.await(); return pairChats.open(roomId, me, partnerMemberId).chatRoomId(); }));
                futures.add(executor.submit(() -> { gate.await(); return pairChats.open(roomId, partner, myMemberId).chatRoomId(); }));
            }
            gate.countDown();
            List<Long> ids = new ArrayList<>();
            for (var future : futures) ids.add(future.get(20, TimeUnit.SECONDS));
            assertThat(ids).containsOnly(ids.getFirst());
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from chat_rooms where type='PAIR' and room_id=?", Long.class, roomId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from chat_room_members where chat_room_id=?", Long.class,
                jdbc.queryForObject("select id from chat_rooms where type='PAIR' and room_id=?", Long.class, roomId)))
                .isEqualTo(2L);
    }
}
