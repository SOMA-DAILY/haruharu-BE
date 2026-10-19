package org.son.haruharu.admin.domain.room;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.implement.VideoReaction;
import org.son.haruharu.domain.video.storage.VideoReactionEntity;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.storage.ChatRoomEntity;
import org.son.haruharu.domain.chat.storage.ChatMessageEntity;
import org.son.haruharu.domain.question.implement.Question;
import org.son.haruharu.domain.question.storage.QuestionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class AdminRoomActivityApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 1, 19, 0);

    @Test
    void givenChoicesAndMatch_whenAdminRead_thenRoomScopedWithUnselectedMembers() throws Exception {
        long room = room();
        long a = member(room, 101), b = member(room, 102);
        member(room, 103);
        long abstained = member(room, 104);
        long outsider = member(room(), 999);
        em.persist(org.son.haruharu.domain.room.storage.FinalSelectionEntity.from(
                org.son.haruharu.domain.room.implement.FinalSelection.of(a, b, START)));
        em.persist(org.son.haruharu.domain.room.storage.FinalSelectionEntity.from(
                org.son.haruharu.domain.room.implement.FinalSelection.of(b, a, START)));
        em.persist(org.son.haruharu.domain.room.storage.FinalSelectionEntity.from(
                org.son.haruharu.domain.room.implement.FinalSelection.of(abstained, null, START)));
        em.persist(org.son.haruharu.domain.room.storage.FinalSelectionEntity.from(
                org.son.haruharu.domain.room.implement.FinalSelection.of(outsider, a, START)));
        em.flush();
        mvc.perform(get("/api/v1/admin/rooms/{id}/selections", room).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].chooser.name").value("참여자101"))
                .andExpect(jsonPath("$.data[0].selected.user_id").value(102))
                .andExpect(jsonPath("$.data[0].mutual").value(true))
                .andExpect(jsonPath("$.data[0].matched").value(false))
                .andExpect(jsonPath("$.data[2].selected").isEmpty())
                .andExpect(jsonPath("$.data[2].submitted").value(false))
                .andExpect(jsonPath("$.data[3].submitted").value(true));
        em.persist(org.son.haruharu.domain.room.storage.MatchResultEntity.from(
                org.son.haruharu.domain.room.implement.MatchResult.of(a, b, START)));
        em.flush();
        mvc.perform(get("/api/v1/admin/rooms/{id}/selections", room).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].matched").value(true))
                .andExpect(jsonPath("$.data[1].matched").value(true));
        mvc.perform(get("/api/v1/admin/rooms/{id}/selections", room)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/rooms/{id}/selections", room).with(authentication(
                new UsernamePasswordAuthenticationToken(101L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/rooms/{id}/selections", 999999).with(authentication(auth())))
                .andExpect(status().isNotFound());
    }

    @Test
    void givenAdminRoles_whenReadSensitiveRoomApis_thenOnlySuperAndOperatorAllowed() throws Exception {
        long room = room();
        for (String endpoint : List.of("activities", "activity/summary", "selections")) {
            for (String role : List.of("SUPER", "OPERATOR", "CS")) {
                var authentication = new UsernamePasswordAuthenticationToken(1L, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                mvc.perform(get("/api/v1/admin/rooms/{id}/" + endpoint, room)
                                .with(authentication(authentication)))
                        .andExpect(role.equals("CS") ? status().isForbidden() : status().isOk());
            }
        }
    }

    private static UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
    }

    private long room() {
        var e = RoomEntity.from(Room.createScheduled(START, START.plusHours(60), START.plusHours(62),
                START.plusHours(63), START.plusDays(3), 3));
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    private long member(long roomId, long userId) {
        var e = RoomMemberEntity.from(RoomMember.of(roomId, userId, null, null, START).withPseudonym("참여자" + userId));
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    private long video(long memberId, LocalDateTime at, boolean deleted) {
        var e = VideoPostEntity.from(new VideoPost(null, memberId, at, null, "오늘의 일상",
                "https://example.com/video.mp4", "https://example.com/thumb.jpg", false, null,
                deleted ? at.plusMinutes(1) : null, at));
        em.persist(e);
        em.flush();
        return e.toDomain().id();
    }

    @Test
    void givenTwoRoomsAndDeletedVideo_whenList_thenIsolatedPagedAndDayBoundaryCorrect() throws Exception {
        long room = room();
        long member = member(room, 101);
        video(member, START.plusHours(23), false);
        long dayTwo = video(member, START.plusHours(24), false);
        // 업로드 시각과 슬롯이 달라도 영상의 일차/필터는 슬롯 기준이어야 한다.
        em.createNativeQuery("update video_posts set created_at = :at where id = :id")
                .setParameter("at", START.plusHours(22)).setParameter("id", dayTwo).executeUpdate();
        video(member, START.plusHours(25), true);
        video(member(room(), 202), START.plusHours(24), false);

        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).with(authentication(auth()))
                        .param("day", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(dayTwo))
                .andExpect(jsonPath("$.data.content[0].type").value("VIDEO"))
                .andExpect(jsonPath("$.data.content[0].day").value(2))
                .andExpect(jsonPath("$.data.content[0].video.slot_start").value("2026-09-02T19:00:00"))
                .andExpect(jsonPath("$.data.content[0].video.thumbnail_url").value("https://example.com/thumb.jpg"));
        mvc.perform(get("/api/v1/admin/rooms/{id}/activity/summary", room).with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.videos").value(2))
                .andExpect(jsonPath("$.data.members[0].videos").value(2));
    }

    @Test
    void givenEmptyOrMissingRoom_whenRead_thenEmptyOr404AndAuthenticationRequired() throws Exception {
        long room = room();
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activity/summary", room).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.counts.videos").value(0));
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", 999999).with(authentication(auth())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).with(authentication(
                new UsernamePasswordAuthenticationToken(101L, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("day", "4").with(authentication(auth())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("type", "INVALID").with(authentication(auth())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("day", "oops").with(authentication(auth())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("page", "2147483647").with(authentication(auth())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void givenAllActivityTypes_whenFiltered_thenCountsPrivacyAndStablePagingMatch() throws Exception {
        long room = room();
        long a = member(room, 101);
        long b = member(room, 102);
        long v = video(a, START.plusHours(1), false);
        em.persist(VideoReactionEntity.from(VideoReaction.create(v, 102L, "HEART", START.plusHours(2))));
        var chatRoom = ChatRoomEntity.from(ChatRoom.createGroup(room));
        em.persist(chatRoom);
        em.flush();
        var chat = ChatMessageEntity.from(new ChatMessage(null, chatRoom.toDomain().id(), 102L, "안녕하세요", START.plusHours(2)));
        em.persist(chat);
        em.persist(ChatMessageEntity.from(ChatMessage.system(chatRoom.toDomain().id(), "운영 안내는 활동 집계 제외", null)));
        var direct = ChatRoomEntity.from(ChatRoom.createDirect(999L));
        em.persist(direct);
        em.flush();
        em.persist(ChatMessageEntity.from(new ChatMessage(null, direct.toDomain().id(), 102L, "직접 대화 제외", START.plusHours(2))));
        var question = QuestionEntity.from(Question.create(room, a, b, 101L, 102L, "참여자102", "좋아하는 음식은?", UUID.randomUUID(), START.plusHours(3))
                .answer("파스타요", START.plusDays(1)));
        em.persist(question);
        em.flush();
        // JPA auditing의 현재 시각 대신 필터 경계가 명확한 fixture 시각으로 맞춘다.
        em.createNativeQuery("update chat_messages set created_at = :at where id = :id")
                .setParameter("at", START.plusHours(2)).setParameter("id", chat.toDomain().id()).executeUpdate();
        em.createNativeQuery("update anonymous_questions set created_at = :at where id = :id")
                .setParameter("at", START.plusHours(3)).setParameter("id", question.toDomain().id()).executeUpdate();

        mvc.perform(get("/api/v1/admin/rooms/{id}/activity/summary", room).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.counts.videos").value(1))
                .andExpect(jsonPath("$.data.counts.chats").value(1)).andExpect(jsonPath("$.data.counts.reactions").value(1))
                .andExpect(jsonPath("$.data.counts.questions").value(1)).andExpect(jsonPath("$.data.counts.answers").doesNotExist())
                .andExpect(jsonPath("$.data.members[0].reactions_received").value(1))
                .andExpect(jsonPath("$.data.members[1].reactions_sent").value(1));
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("type", "QUESTION").param("order", "asc").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].actor.user_id").value(101))
                .andExpect(jsonPath("$.data.content[0].actor.name").value("참여자101"))
                .andExpect(jsonPath("$.data.content[0].target.user_id").value(102))
                .andExpect(jsonPath("$.data.content[0].answer").value("파스타요"))
                .andExpect(jsonPath("$.data.content[0].sender_user_id").doesNotExist())
                .andExpect(jsonPath("$.data.content[1]").doesNotExist());
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("type", "QUESTION").param("userId", "101").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total_elements").value(1));
        em.persist(org.son.haruharu.domain.profile.storage.UserProfileEntity.from(
                new org.son.haruharu.domain.profile.implement.UserProfile(null, 101L, null, null, null, null,
                        "테스트작성자", null, null, null)));
        em.flush();
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("type", "QUESTION").param("order", "asc").with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].actor.name").value("참여자101"))
                .andExpect(jsonPath("$.data.content[0].target.name").value("참여자102"));
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("day", "2").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total_elements").value(0));
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("size", "1").param("page", "1").param("order", "asc").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total_elements").value(4))
                .andExpect(jsonPath("$.data.content[0].type").value("CHAT"));
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("size", "1").param("page", "2").param("order", "asc").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].type").value("REACTION"));
        em.remove(chat);
        em.remove(question);
        em.flush();
        mvc.perform(get("/api/v1/admin/rooms/{id}/activity/summary", room).with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.counts.chats").value(0))
                .andExpect(jsonPath("$.data.counts.questions").value(0)).andExpect(jsonPath("$.data.counts.answers").doesNotExist());
    }

    @Test
    @DisplayName("채팅 활동 조회 시 일반 글·글 답장·영상 답장의 답장 종류가 구분되어 내려온다")
    void givenPlainMessageReplyAndVideoReply_whenListChats_thenReplyTypeDistinguished() throws Exception {
        long room = room();
        long a = member(room, 101);
        member(room, 102);
        long v = video(a, START.plusHours(1), false);
        var chatRoom = ChatRoomEntity.from(ChatRoom.createGroup(room));
        em.persist(chatRoom);
        em.flush();
        long chatRoomId = chatRoom.toDomain().id();
        var plain = ChatMessageEntity.from(new ChatMessage(null, chatRoomId, 101L, "안녕하세요", START.plusHours(2)));
        em.persist(plain);
        em.flush();
        var reply = ChatMessageEntity.from(new ChatMessage(null, chatRoomId, 102L, "반가워요", null, null, null,
                plain.toDomain().id(), "참여자101", "안녕하세요"));
        em.persist(reply);
        var videoReply = ChatMessageEntity.from(ChatMessage.videoReply(chatRoomId, 102L, "영상 잘 봤어요", v, "req-1"));
        em.persist(videoReply);
        em.flush();
        // JPA auditing의 현재 시각 대신 순서가 명확한 fixture 시각으로 맞춘다.
        long[] ids = {plain.toDomain().id(), reply.toDomain().id(), videoReply.toDomain().id()};
        for (int i = 0; i < ids.length; i++) {
            em.createNativeQuery("update chat_messages set created_at = :at where id = :id")
                    .setParameter("at", START.plusHours(2).plusMinutes(i)).setParameter("id", ids[i]).executeUpdate();
        }

        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("type", "CHAT").param("order", "asc").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.page.total_elements").value(3))
                .andExpect(jsonPath("$.data.content[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$.data.content[0].reply_type").isEmpty())
                .andExpect(jsonPath("$.data.content[1].reply_type").value("MESSAGE"))
                .andExpect(jsonPath("$.data.content[2].reply_type").value("VIDEO"));
        mvc.perform(get("/api/v1/admin/rooms/{id}/activities", room).param("type", "VIDEO").with(authentication(auth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].reply_type").isEmpty());
    }
}
