package org.son.haruharu.domain.replyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.replyrecommendation.business.ReplyDepthLabelService;
import org.son.haruharu.domain.replyrecommendation.implement.HoldoutAssigner;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyLinkRecorder;
import org.son.haruharu.domain.replyrecommendation.implement.SavedVideoReply;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.WithdrawnUserPurgeRepository;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 추천 효과 검증(이슈 #560)을 실제 PostgreSQL 과 배포 SQL(558 → 559 → 561)로 확인한다.
 * 게시자 응답 라벨, 시청자 단위 무작위 비교 그룹(HOLDOUT 이면 카드·작업 없음), 답장 그룹 기록, 탈퇴 파기.
 */
@SpringBootTest(properties = {
        "haruharu.reply-recommendation.enabled=true",
        "haruharu.reply-recommendation.ensure-rate-limit-per-minute=1000",
        "haruharu.reply-recommendation.collection.real-user-enabled=true",
        "haruharu.reply-recommendation.holdout.ratio=0.5",
        "haruharu.reply-recommendation.holdout.salt=test-salt",
        "haruharu.reply-recommendation.depth.enabled=true",
        "haruharu.reply-recommendation.depth.cron=-",
        "haruharu.reply-recommendation.snapshot.initial-delay-ms=3600000",
        "haruharu.reply-recommendation.snapshot.sweep-delay-ms=3600000",
        "haruharu.reply-recommendation.maintenance-cron=-",
        "haruharu.reply-recommendation.purge-cron=-"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplyEffectIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static volatile boolean schemaApplied;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) throws Exception {
        applySchema();
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private static synchronized void applySchema() throws Exception {
        if (schemaApplied) {
            return;
        }
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("scripts/sql"))) {
            root = root.getParent();
        }
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var s = c.createStatement()) {
            s.execute(Files.readString(root.resolve("scripts/sql/558-reply-recommendations-up.sql")));
            s.execute(Files.readString(root.resolve("scripts/sql/559-reply-depth-up.sql")));
            s.execute(Files.readString(root.resolve("scripts/sql/561-reply-effect-up.sql")));
            s.execute(Files.readString(root.resolve("scripts/sql/561-reply-effect-up.sql")));   // 멱등
        }
        schemaApplied = true;
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired VideoPostJpaRepository videos;
    @Autowired ChatService chats;
    @Autowired ReplyDepthLabelService labels;
    @Autowired ReplyLinkRecorder links;
    @Autowired HoldoutAssigner holdoutAssigner;
    @Autowired WithdrawnUserPurgeRepository purge;
    @Autowired PlatformTransactionManager tx;
    @Autowired Clock clock;

    Long roomId, chatId, uploader, uploaderMember, videoId;
    List<Long> viewers;
    LocalDateTime now;

    @BeforeEach
    void setUp() {
        jdbc.update("""
                INSERT INTO ai_reply.version_registry
                    (version_id, config_json, prompts_json, full_config_sha, prompts_sha, preprocess_version,
                     gallery_snapshot_id, status)
                VALUES ('v1-test', '{}', '{}', 'c', 'p', 'pre-1', 'gallery-1', 'ACTIVE')
                ON CONFLICT DO NOTHING""");
        jdbc.update("""
                INSERT INTO ai_reply.active_version (scope, version_id, revision) VALUES ('online', 'v1-test', 1)
                ON CONFLICT (scope) DO NOTHING""");
        now = LocalDateTime.now(clock).withNano(0);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(40), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3)
                .withReplyRecommendationEnabled(true))).toDomain().id();
        uploader = user();
        uploaderMember = member(uploader);
        viewers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Long v = user();
            member(v);
            viewers.add(v);
        }
        chats.createGroupRoom(roomId);
        chatId = jdbc.queryForObject("SELECT id FROM chat_rooms WHERE room_id = ? AND type = 'GROUP'", Long.class, roomId);
        LocalDateTime published = now.minusHours(35);
        videoId = videos.save(VideoPostEntity.from(VideoPost.create(uploaderMember, published.withMinute(0), null,
                "캡션", "https://media.test/v/" + UUID.randomUUID() + ".mp4", "https://media.test/t.jpg",
                published))).toDomain().id();
    }

    Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    Long member(Long userId) {
        return members.save(RoomMemberEntity.from(new RoomMember(null, roomId, userId, "MALE", "멤버" + userId, null,
                now.minusHours(40)))).toDomain().id();
    }

    long message(long sender, LocalDateTime at, String hiddenColumn) {
        Long id = jdbc.queryForObject("""
                INSERT INTO chat_messages (chat_room_id, sender_id, sender_type, content, message_type,
                                           reference_suppressed, created_at, updated_at)
                VALUES (?, ?, 'USER', 'x', 'TEXT', false, ?, ?) RETURNING id""", Long.class, chatId, sender, at, at);
        if (hiddenColumn != null) {
            jdbc.update("UPDATE chat_messages SET " + hiddenColumn + " = ? WHERE id = ?", at, id);
        }
        return id;
    }

    void link(long messageId, long viewer, long video, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO ai_reply.reply_link (message_id, viewer_id, video_id, sent_at, source_status, attribution)
                VALUES (?, ?, ?, ?, 'NONE', 'unexposed')""",
                messageId, viewer, video, at.atZone(clock.getZone()).toOffsetDateTime());
    }

    Map<String, Object> observation(long messageId) {
        return jdbc.queryForMap("SELECT * FROM ai_reply.reply_depth_observation WHERE message_id = ?", messageId);
    }

    MvcResult call(boolean ensure, Long who) throws Exception {
        String url = "/api/v1/rooms/" + roomId + "/video-posts/" + videoId + "/reply-recommendations";
        var auth = authentication(new UsernamePasswordAuthenticationToken(who, null, List.of()));
        return mvc.perform((ensure ? post(url) : get(url)).with(auth)).andReturn();
    }

    static String reason(MvcResult r) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), "$.data.reason_code");
    }

    String arm(Long userId) {
        return jdbc.queryForObject("SELECT arm FROM ai_reply.holdout_assignment WHERE room_id = ? AND user_id = ?",
                String.class, roomId, userId);
    }

    @Test
    @DisplayName("게시자 응답: 창 안(경계 정각 포함)이면 true 와 첫 메시지까지 분, 창 밖·말 안 함·숨긴 글뿐이면 false, 작성자 모르면 비움")
    void givenOwnerMessages_whenCollected_thenResponseLabel() {
        LocalDateTime base = now.minusHours(34);
        long v1 = viewers.get(0);
        long inWindow = message(v1, base, null);
        message(viewers.get(1), base.plusMinutes(5), null);                // 대조: 다른 멤버 1명 말함
        message(uploader, base.plusMinutes(10), null);
        link(inWindow, v1, videoId, base);

        LocalDateTime b2 = base.plusHours(2);
        long edge = message(v1, b2, null);
        message(uploader, b2.plusMinutes(30), null);                        // 경계 정각
        link(edge, v1, videoId, b2);

        LocalDateTime b3 = base.plusHours(4);
        long late = message(v1, b3, null);
        message(uploader, b3.plusMinutes(31), null);                        // 창 밖
        link(late, v1, videoId, b3);

        LocalDateTime b4 = base.plusHours(6);
        long silent = message(v1, b4, null);
        message(uploader, b4.minusMinutes(1), null);                        // id 는 뒤지만 시각은 답장 전: 세지 않음
        message(viewers.get(2), b4.minusMinutes(1), null);
        link(silent, v1, videoId, b4);

        LocalDateTime b5 = base.plusHours(8);
        long hiddenOnly = message(v1, b5, null);
        message(uploader, b5.plusMinutes(5), "admin_deleted_at");           // 숨긴 글은 세지 않음
        link(hiddenOnly, v1, videoId, b5);

        LocalDateTime b6 = base.plusHours(9);
        long unknownOwner = message(v1, b6, null);
        link(unknownOwner, v1, 999_999L, b6);                               // 없는 영상

        assertThat(labels.collectDue()).isEqualTo(6);

        assertThat(observation(inWindow)).containsEntry("owner_responded", true)
                .containsEntry("owner_response_minutes", new BigDecimal("10.00"))
                .containsEntry("response_window_minutes", 30)
                .containsEntry("bystander_members", 4)        // 6명 - 답장자 - 작성자
                .containsEntry("bystanders_responded", 1);
        assertThat(observation(edge)).containsEntry("owner_responded", true)
                .containsEntry("owner_response_minutes", new BigDecimal("30.00"));
        assertThat(observation(late)).containsEntry("owner_responded", false)
                .containsEntry("owner_response_minutes", null);
        assertThat(observation(silent)).containsEntry("owner_responded", false)
                .containsEntry("bystanders_responded", 0);
        assertThat(observation(hiddenOnly)).containsEntry("owner_responded", false);
        assertThat(observation(unknownOwner)).containsEntry("owner_responded", null)
                .containsEntry("response_window_minutes", null).containsEntry("bystander_members", null);
        // Depth(학습 라벨)는 그대로 버튼 사슬만 센다.
        assertThat(observation(inWindow)).containsEntry("depth", 0);
    }

    @Test
    @DisplayName("비교 그룹: 방 멤버를 해시 순으로 절반씩 나누고, HOLDOUT 은 POST·GET 모두 UNAVAILABLE/HOLDOUT 이며 작업을 만들지 않는다")
    void givenHoldoutRatio_whenViewersRequest_thenHalfHoldoutWithoutJobs() throws Exception {
        List<Long> holdout = new ArrayList<>();
        List<Long> treatment = new ArrayList<>();
        for (Long v : viewers) {
            MvcResult r = call(true, v);
            if ("HOLDOUT".equals(arm(v))) {
                holdout.add(v);
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                assertThat(reason(r)).isEqualTo("HOLDOUT");
                assertThat(reason(call(false, v))).isEqualTo("HOLDOUT");
            } else {
                treatment.add(v);
                assertThat(r.getResponse().getStatus()).isIn(200, 202);
                assertThat(reason(r)).isNotEqualTo("HOLDOUT");
            }
        }
        // 6명(작성자 포함) 방이라 첫 요청 때 3:3 으로 배정된다.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.holdout_assignment WHERE room_id = ?",
                Integer.class, roomId)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ai_reply.holdout_assignment WHERE room_id = ? AND arm = 'HOLDOUT'",
                Integer.class, roomId)).isEqualTo(3);
        assertThat(holdout.size() + treatment.size()).isEqualTo(5);
        for (Long v : holdout) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_request WHERE viewer_id = ?",
                    Integer.class, v)).isZero();
        }
        for (Long v : treatment) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_request WHERE viewer_id = ?",
                    Integer.class, v)).isEqualTo(1);
        }

        // 늦게 들어온 멤버도 규칙대로 배정되고, 기존 배정은 바뀌지 않는다.
        Map<Long, String> before = new java.util.HashMap<>();
        for (Long v : viewers) {
            before.put(v, arm(v));
        }
        Long late = user();
        member(late);
        holdoutAssigner.armOf(roomId, late, clock.instant());   // 단체 채팅 입장 전이라 배정기를 직접 부른다
        assertThat(arm(late)).isIn("TREATMENT", "HOLDOUT");
        for (Long v : viewers) {
            assertThat(arm(v)).isEqualTo(before.get(v));
        }

        // 답장을 저장하면 보낸 사람의 그룹을 남긴다.
        Long someone = viewers.get(0);
        long reply = message(someone, now, null);
        new TransactionTemplate(tx).executeWithoutResult(s -> links.record(new SavedVideoReply(reply, someone, videoId,
                now, null, null, null, roomId), clock.instant()));
        assertThat(jdbc.queryForObject("SELECT experiment_arm FROM ai_reply.reply_link WHERE message_id = ?",
                String.class, reply)).isEqualTo(arm(someone));

        // 탈퇴 파기 때 배정도 지운다.
        new TransactionTemplate(tx).executeWithoutResult(s -> purge.purgePersonalData(someone, now));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.holdout_assignment WHERE user_id = ?",
                Integer.class, someone)).isZero();
    }
}
