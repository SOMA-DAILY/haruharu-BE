package org.son.haruharu.domain.chat;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.son.haruharu.domain.chat.business.*;
import org.son.haruharu.domain.chat.business.exception.ChatReplyInvalidException;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** 채팅 메시지 반응과 답장(#456) — PostgreSQL 에서 UNIQUE·동시성·응답 모양 확인. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatReactionReplyApiIntegrationTest {
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
    @Autowired ChatMessageReactionService reactions;
    @Autowired Clock clock;

    Long roomId, chatId, owner;
    List<Long> others = new ArrayList<>();

    @BeforeEach
    void setup() {
        var now = LocalDateTime.now(clock);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3))).toDomain().id();
        owner = user();
        member(owner, "선우", now);
        others.clear();
        String[] names = {"윤슬", "민준", "서준", "도윤", "하린"};
        for (String name : names) {
            Long id = user();
            member(id, name, now);
            others.add(id);
        }
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, owner).chatRoomId();
    }

    Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    void member(Long id, String name, LocalDateTime now) {
        members.save(RoomMemberEntity.from(new RoomMember(null, roomId, id, "MALE", name, null, now)));
    }

    UsernamePasswordAuthenticationToken auth(Long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of());
    }

    @Test
    @DisplayName("5명이 동시에 반응해도 개수가 정확하고, 같은 유저의 동시 첫 반응은 1행으로 남는다")
    void concurrentTogglesKeepExactCount() throws Exception {
        Long messageId = chats.sendMessage(chatId, owner, "저 아직 침대 위 ㅋㅋ").messageId();
        var executor = Executors.newFixedThreadPool(7);
        var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<?>>();
            for (Long id : others) {
                futures.add(executor.submit(() -> {
                    gate.await();
                    return reactions.toggle(id, chatId, messageId, ReactionType.LAUGH);
                }));
            }
            Long twice = others.get(0);
            futures.add(executor.submit(() -> {
                gate.await();
                return reactions.toggle(twice, chatId, messageId, ReactionType.LAUGH);
            }));
            gate.countDown();
            for (var f : futures) f.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from chat_message_reactions where message_id=?",
                Long.class, messageId)).isEqualTo(5L);

        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth(others.get(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].reactions[0].type").value("LAUGH"))
                .andExpect(jsonPath("$.data.messages[0].reactions[0].count").value(5))
                .andExpect(jsonPath("$.data.messages[0].my_reaction").value("LAUGH"));
    }

    @Test
    @DisplayName("반응 API: 토글 응답, 내 메시지 403, 주인 아닌 reactions 조회 403, 주인은 가명 목록")
    void reactionApi() throws Exception {
        Long messageId = chats.sendMessage(chatId, owner, "안녕").messageId();
        String base = "/api/v1/chat-rooms/" + chatId + "/messages/" + messageId;

        mvc.perform(put(base + "/reaction").with(authentication(auth(others.get(0))))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LAUGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts[0].type").value("LAUGH"))
                .andExpect(jsonPath("$.data.counts[0].count").value(1))
                .andExpect(jsonPath("$.data.my_reaction").value("LAUGH"));
        mvc.perform(put(base + "/reaction").with(authentication(auth(owner)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LAUGH\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_REACTION_OWN_MESSAGE"));
        mvc.perform(get(base + "/reactions").with(authentication(auth(others.get(1)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_REACTION_OWNER_ONLY"));
        mvc.perform(get(base + "/reactions").with(authentication(auth(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("윤슬"))
                .andExpect(jsonPath("$.data[0].type").value("LAUGH"));
        // 같은 반응 다시 → 취소
        mvc.perform(put(base + "/reaction").with(authentication(auth(others.get(0))))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"laugh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts").isEmpty())
                .andExpect(jsonPath("$.data.my_reaction").isEmpty());
    }

    @Test
    @DisplayName("답장: 기존 키 이름·타입은 그대로, reply_to 추가. 원문 보낸 사람을 차단한 조회자에겐 available=false")
    void replyKeepsLegacyKeysAndHidesBlocked() throws Exception {
        Long originalId = chats.sendMessage(chatId, owner, "저 아직 침대 위 ㅋㅋ").messageId();
        Long replier = others.get(0);
        Long replyId = chats.sendMessage(chatId, replier, "저도요", originalId).messageId();

        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth(others.get(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].message_id").value(replyId))
                .andExpect(jsonPath("$.data.messages[0].sender_id").value(replier))
                .andExpect(jsonPath("$.data.messages[0].content").value("저도요"))
                .andExpect(jsonPath("$.data.messages[0].sent_at").isString())
                .andExpect(jsonPath("$.data.messages[0].reactions").isArray())
                .andExpect(jsonPath("$.data.messages[0].reply_to.message_id").value(originalId))
                .andExpect(jsonPath("$.data.messages[0].reply_to.sender_name").value("선우"))
                .andExpect(jsonPath("$.data.messages[0].reply_to.preview").value("저 아직 침대 위 ㅋㅋ"))
                .andExpect(jsonPath("$.data.messages[0].reply_to.available").value(true))
                .andExpect(jsonPath("$.data.messages[1].reply_to").isEmpty());

        Long blocker = others.get(2);
        jdbc.update("insert into user_blocks (blocker_user_id, blocked_user_id, created_at, updated_at) values (?,?,now(),now())",
                blocker, owner);
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth(blocker))))
                .andExpect(jsonPath("$.data.messages[0].reply_to.available").value(false))
                .andExpect(jsonPath("$.data.messages[0].reply_to.preview").isEmpty());
    }

    @Test
    @DisplayName("다른 채팅방 메시지에 답장하면 CHAT_REPLY_INVALID 이고 저장되지 않는다")
    void replyToOtherRoomIsRejected() {
        assertThatThrownBy(() -> chats.sendMessage(chatId, owner, "x", 987654L))
                .isInstanceOf(ChatReplyInvalidException.class);
        assertThat(jdbc.queryForObject("select count(*) from chat_messages where chat_room_id=?", Long.class, chatId))
                .isZero();
    }
}
