package org.son.haruharu.domain.chat;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.son.haruharu.domain.chat.business.*;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.room.storage.*;
import org.son.haruharu.domain.user.implement.*;
import org.son.haruharu.domain.user.storage.*;
import org.son.haruharu.domain.video.implement.*;
import org.son.haruharu.domain.video.storage.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VideoReplyApiIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired VideoPostJpaRepository videos;
    @Autowired ChatService chats;
    @Autowired VideoReplyService replies;
    @Autowired PlatformTransactionManager tx;
    @Autowired Clock clock;
    Long roomId, chatId, sender, owner, observer, outsider, videoId;

    @BeforeEach
    void setup() {
        var now = LocalDateTime.now(clock);
        roomId =
                rooms.save(
                                RoomEntity.from(
                                        Room.createScheduled(
                                                now.minusHours(25),
                                                now.plusHours(23),
                                                now.plusHours(25),
                                                now.plusHours(26),
                                                now.plusHours(47),
                                                3)))
                        .toDomain()
                        .id();
        sender = user();
        owner = user();
        observer = user();
        outsider = user();
        member(sender, "민준", now);
        var author = member(owner, "도윤", now);
        member(observer, "서준", now);
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, sender).chatRoomId();
        videoId =
                videos.save(
                                VideoPostEntity.from(
                                        VideoPost.create(
                                                author,
                                                now.minusHours(1),
                                                null,
                                                "테스트",
                                                "https://example.com/video.mp4",
                                                "https://example.com/thumb.jpg",
                                                now)))
                        .toDomain()
                        .id();
    }

    Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    Long member(Long id, String name, LocalDateTime now) {
        return members.save(
                        RoomMemberEntity.from(
                                new RoomMember(null, roomId, id, "MALE", name, null, now)))
                .toDomain()
                .id();
    }

    String base() {
        return "/api/v1/rooms/" + roomId + "/video-posts/" + videoId + "/reply";
    }

    UsernamePasswordAuthenticationToken auth(Long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of());
    }

    long count() {
        return jdbc.queryForObject(
                "select count(*) from chat_messages where chat_room_id=?", Long.class, chatId);
    }

    @Test
    void concurrentRetriesPersistAndNotifyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        var executor = Executors.newFixedThreadPool(6);
        var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Long>>();
            for (int i = 0; i < 6; i++)
                futures.add(
                        executor.submit(
                                () -> {
                                    gate.await();
                                    return replies.send(roomId, videoId, sender, "영상 좋아요", key)
                                            .messageId();
                                }));
            gate.countDown();
            var ids = new HashSet<Long>();
            for (var f : futures) ids.add(f.get(20, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(count()).isEqualTo(1);
            assertThat(
                            jdbc.queryForList(
                                    "select user_id from notification_history where"
                                        + " type='VIDEO_REPLY' and user_id in (?,?,?)",
                                    Long.class,
                                    sender,
                                    owner,
                                    observer))
                    .containsExactly(owner);
        } finally {
            executor.shutdownNow();
        }
        mvc.perform(
                        get("/api/v1/chat-rooms/" + chatId + "/messages")
                                .with(authentication(auth(observer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].video_post_id").value(videoId));
    }

    @Test
    void rollbackDoesNotPersistMessageOrNotification() {
        new TransactionTemplate(tx)
                .executeWithoutResult(
                        s -> {
                            replies.send(
                                    roomId,
                                    videoId,
                                    sender,
                                    "rollback",
                                    UUID.randomUUID().toString());
                            s.setRollbackOnly();
                        });
        assertThat(count()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from notification_history where type='VIDEO_REPLY'"
                                    + " and user_id=?",
                                Long.class,
                                owner))
                .isZero();
    }

    @Test
    void apiValidationAndDeletedPreview() throws Exception {
        mvc.perform(post(base())).andExpect(status().isUnauthorized());
        String body = "{\"content\":\"영상 좋아요\",\"request_id\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(
                        post(base())
                                .with(authentication(auth(outsider)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post(base())
                                .with(authentication(auth(owner)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post(base())
                                .with(authentication(auth(sender)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.video_post_id").value(videoId));
        jdbc.update(
                "update video_posts set deleted_at=? where id=?",
                LocalDateTime.now(clock),
                videoId);
        mvc.perform(get(base()).with(authentication(auth(observer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("DELETED"))
                .andExpect(jsonPath("$.data.video_url").doesNotExist());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void hiddenAndClosedRoomAreRejected() {
        jdbc.update("update video_posts set is_hidden=true where id=?", videoId);
        assertThatThrownBy(
                        () ->
                                replies.send(
                                        roomId,
                                        videoId,
                                        sender,
                                        "hi",
                                        UUID.randomUUID().toString()))
                .isInstanceOf(RuntimeException.class);
        jdbc.update("update video_posts set is_hidden=false where id=?", videoId);
        jdbc.update(
                "update rooms set ends_at=? where id=?",
                LocalDateTime.now(clock).minusMinutes(1),
                roomId);
        assertThatThrownBy(
                        () ->
                                replies.send(
                                        roomId,
                                        videoId,
                                        sender,
                                        "hi",
                                        UUID.randomUUID().toString()))
                .hasMessageContaining("방");
        assertThat(count()).isZero();
    }
}
