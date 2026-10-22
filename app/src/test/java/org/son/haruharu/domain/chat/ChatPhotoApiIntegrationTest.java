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
class ChatPhotoApiIntegrationTest {
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
    @Autowired ChatPhotoService photos;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.son.haruharu.infrastructure.s3.S3StoragePort storage;
    @Autowired PlatformTransactionManager tx;
    @Autowired Clock clock;
    Long roomId, chatId, sender, owner, observer, outsider, videoId;

    @BeforeEach
    void setup() {
        var now = LocalDateTime.now(clock);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3))).toDomain().id();
        sender = user(); owner = user(); outsider = user();
        member(sender, "테스트 가명 A", now); member(owner, "테스트 가명 B", now);
        chats.createGroupRoom(roomId);
        chatId = chats.getGroupRoomBySourceRoom(roomId, sender).chatRoomId();
        org.mockito.BDDMockito.given(storage.publicBaseUrl()).willReturn("https://media.example/");
        org.mockito.BDDMockito.given(storage.createPresignedImageGetUrl(org.mockito.ArgumentMatchers.anyString()))
                .willAnswer(call -> call.getArgument(0));
    }
    Long user() { return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id(); }
    void member(Long id, String name, LocalDateTime now) {
        members.save(RoomMemberEntity.from(new RoomMember(null, roomId, id, "MALE", name, null, now)));
    }
    UsernamePasswordAuthenticationToken auth(Long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of());
    }
    byte[] png(int width) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(width, 16,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
    long count() { return jdbc.queryForObject("select count(*) from chat_messages where chat_room_id=?", Long.class, chatId); }

    @Test @DisplayName("동시 재전송은 사진 한 장·메시지 하나만 만들고 다른 파일의 요청 ID 재사용은 거절한다")
    void givenConcurrentRetries_whenSend_thenPersistsOnce() throws Exception {
        String key = UUID.randomUUID().toString(); byte[] bytes = png(24);
        var executor = Executors.newFixedThreadPool(4); var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Long>>();
            for (int i = 0; i < 4; i++) futures.add(executor.submit(() -> {
                gate.await(); return photos.send(chatId, sender, key, bytes, "image/png").messageId();
            }));
            gate.countDown(); var ids = new HashSet<Long>();
            for (var f : futures) ids.add(f.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1); assertThat(count()).isEqualTo(1);
            org.mockito.Mockito.verify(storage, org.mockito.Mockito.times(1)).putImage(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("image/png"),
                    org.mockito.ArgumentMatchers.any());
            byte[] different = png(28);
            assertThatThrownBy(() -> photos.send(chatId, sender, key, different, "image/png"))
                    .isInstanceOf(org.son.haruharu.domain.chat.business.exception.ChatPhotoRequestConflictException.class);
        } finally { executor.shutdownNow(); }
    }

    @Test @DisplayName("사진 송신·수신 목록·사진 답장과 만료 주소 갱신 계약을 확인한다")
    void givenPhoto_whenSendListAndReply_thenExposesMetadata() throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "test.png", "image/png", png(24));
        String response = mvc.perform(multipart("/api/v1/chat-rooms/" + chatId + "/photo-messages")
                .file(file).param("request_id", UUID.randomUUID().toString()).with(authentication(auth(sender))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("사진"))
                .andExpect(jsonPath("$.data.photo.width").value(24))
                .andExpect(jsonPath("$.data.client_request_id").isString())
                .andExpect(jsonPath("$.data.photo.digest").doesNotExist()).andReturn().getResponse().getContentAsString();
        Long id = jdbc.queryForObject("select max(id) from chat_messages where chat_room_id=?", Long.class, chatId);
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages[0].photo.height").value(16));
        var reply = chats.sendMessage(chatId, owner, "사진 답장 테스트", id);
        assertThat(reply.replyTo().photo().width()).isEqualTo(24);
        assertThat(reply.replyTo().photo().url()).isNull();
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth(sender))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages[0].reply_to.preview").value("사진"))
                .andExpect(jsonPath("$.data.messages[0].reply_to.photo.width").value(24));
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/" + id + "/photo").with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.width").value(24));
        assertThat(response).doesNotContain("photo_digest");
    }

    @Test @DisplayName("참여하지 않은 계정과 퇴장한 계정은 송수신 사진에 접근할 수 없다")
    void givenOutsiderOrLeftMember_whenAccess_thenDenied() throws Exception {
        var sent = photos.send(chatId, sender, UUID.randomUUID().toString(), png(20), "image/png");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "test.png", "image/png", png(20));
        mvc.perform(multipart("/api/v1/chat-rooms/" + chatId + "/photo-messages").file(file)
                .param("request_id", UUID.randomUUID().toString()).with(authentication(auth(outsider))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/" + sent.messageId() + "/photo")
                .with(authentication(auth(outsider)))).andExpect(status().isForbidden());
        chats.leaveRoom(chatId, owner);
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/" + sent.messageId() + "/photo")
                .with(authentication(auth(owner)))).andExpect(status().isForbidden());
    }

    @Test @DisplayName("메시지 트랜잭션이 롤백되면 업로드 객체를 정리하고 메시지와 알림을 남기지 않는다")
    void givenRollback_whenSend_thenCleansUpload() throws Exception {
        byte[] bytes = png(20);
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            photos.send(chatId, sender, UUID.randomUUID().toString(), bytes, "image/png");
            status.setRollbackOnly();
        });
        assertThat(count()).isZero();
        org.mockito.Mockito.verify(storage).deleteObjects(org.mockito.ArgumentMatchers.anyList());
    }

    @Test @DisplayName("삭제된 사진과 다른 방의 사진은 조회할 수 없고 목록의 사진 참조도 숨긴다")
    void givenDeletedOrForeignPhoto_whenGet_thenNotFound() throws Exception {
        var sent = photos.send(chatId, sender, UUID.randomUUID().toString(), png(20), "image/png");
        jdbc.update("update chat_messages set admin_deleted_at=now() where id=?", sent.messageId());
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages/" + sent.messageId() + "/photo")
                .with(authentication(auth(owner)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/chat-rooms/" + chatId + "/messages").with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.messages[0].photo").isEmpty());
    }
}
