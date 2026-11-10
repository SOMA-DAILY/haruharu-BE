package org.son.haruharu.domain.replyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.replyrecommendation.business.ReplyDepthLabelService;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.storage.RoomEntity;
import org.son.haruharu.domain.room.storage.RoomJpaRepository;
import org.son.haruharu.domain.room.storage.RoomMemberEntity;
import org.son.haruharu.domain.room.storage.RoomMemberJpaRepository;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.son.haruharu.domain.user.implement.WithdrawnUserPurgeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Depth 라벨 수집(#559)을 실제 PostgreSQL·배포 SQL(558 → 559)과 Hibernate 가 만든 chat_messages 로 확인한다.
 * 채팅·답장 연결 행은 테스트가 직접 넣는다(본문은 쓰지 않는다).
 */
@SpringBootTest(properties = {
        "haruharu.reply-recommendation.depth.enabled=true",
        "haruharu.reply-recommendation.depth.cron=-",
        "haruharu.reply-recommendation.snapshot.initial-delay-ms=3600000",
        "haruharu.reply-recommendation.snapshot.sweep-delay-ms=3600000",
        "haruharu.reply-recommendation.maintenance-cron=-",
        "haruharu.reply-recommendation.purge-cron=-"})
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplyDepthLabelIntegrationTest {

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
        }
        schemaApplied = true;
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ReplyDepthLabelService service;
    @Autowired WithdrawnUserPurgeRepository purge;
    @Autowired PlatformTransactionManager tx;
    @Autowired Clock clock;
    @Autowired UserJpaRepository users;
    @Autowired RoomJpaRepository rooms;
    @Autowired RoomMemberJpaRepository members;
    @Autowired VideoPostJpaRepository videos;

    private static final long ROOM = 9001L;
    private static final long OTHER_ROOM = 9002L;
    private static final long VIEWER = 501L;
    private static final long PARTNER = 502L;

    LocalDateTime sent;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM ai_reply.reply_depth_observation");
        jdbc.update("DELETE FROM ai_reply.reply_link");
        jdbc.update("DELETE FROM ai_reply.reply_episode");
        jdbc.update("DELETE FROM chat_messages");
        sent = LocalDateTime.now(clock).minusHours(30).withNano(0);
    }

    private long message(long room, long sender, Long replyTo, LocalDateTime at, String hiddenColumn) {
        Long id = jdbc.queryForObject("""
                INSERT INTO chat_messages (chat_room_id, sender_id, sender_type, content, message_type,
                                           reply_to_message_id, reference_suppressed, created_at, updated_at)
                VALUES (?, ?, 'USER', 'x', 'TEXT', ?, false, ?, ?) RETURNING id""",
                Long.class, room, sender, replyTo, at, at);
        if (hiddenColumn != null) {
            jdbc.update("UPDATE chat_messages SET " + hiddenColumn + " = ? WHERE id = ?", at, id);
        }
        return id;
    }

    private void link(long messageId, LocalDateTime at, String status) {
        link(messageId, VIEWER, 77L, at, status, null);
    }

    private void link(long messageId, long viewer, long video, LocalDateTime at, String status, Long episodeId) {
        jdbc.update("""
                INSERT INTO ai_reply.reply_link (message_id, viewer_id, video_id, sent_at, source_set_id,
                                                 source_card_id, source_status, attribution, episode_id)
                VALUES (?, ?, ?, ?, 'set-1', 'c1', ?, 'explicit', ?)""",
                messageId, viewer, video, at.atZone(clock.getZone()).toOffsetDateTime(), status, episodeId);
    }

    private long episode(String provenance, LocalDateTime at) {
        return jdbc.queryForObject("""
                INSERT INTO ai_reply.reply_episode (session_id, set_id, request_id, viewer_id, video_id, room_id,
                                                    first_impression_at, last_impression_at, provenance)
                VALUES (?, 'set-1', 'req-1', ?, 77, 1, ?, ?, ?) RETURNING id""", Long.class,
                UUID.randomUUID().toString(), VIEWER, at.atZone(clock.getZone()).toOffsetDateTime(),
                at.atZone(clock.getZone()).toOffsetDateTime(), provenance);
    }

    private Map<String, Object> observation(long messageId) {
        return jdbc.queryForMap("SELECT * FROM ai_reply.reply_depth_observation WHERE message_id = ?", messageId);
    }

    @Test
    @DisplayName("기간이 끝난 답장만 같은 방·기간 안 답글로 Depth·Width 를 남기고, 다시 돌려도 그대로다")
    void givenEndedWindow_whenCollected_thenLabelledOnceWithinWindowAndRoom() {
        long r = message(ROOM, VIEWER, null, sent, null);
        long a = message(ROOM, PARTNER, r, sent.plusMinutes(1), null);
        long b = message(ROOM, VIEWER, a, sent.plusMinutes(2), null);
        message(ROOM, PARTNER, b, sent.plusMinutes(3), null);                 // r→상대→나→상대 = 3
        message(ROOM, PARTNER, r, sent.plusMinutes(4), "admin_deleted_at");   // 삭제된 끝 답글은 세지 않음
        message(ROOM, PARTNER, b, sent.plusHours(25), null);                  // 기간 밖
        message(OTHER_ROOM, PARTNER, r, sent.plusMinutes(5), null);           // 다른 방
        link(r, sent, "VALID");

        long fresh = message(ROOM, VIEWER, null, LocalDateTime.now(clock).minusHours(1), null);
        link(fresh, LocalDateTime.now(clock).minusHours(1), "NONE");

        assertThat(service.collectDue()).isEqualTo(1);
        assertThat(service.collectDue()).isZero();

        Map<String, Object> row = observation(r);
        assertThat(row.get("status")).isEqualTo("OBSERVED");
        assertThat(row.get("depth")).isEqualTo(3);
        assertThat(row.get("width")).isEqualTo(1);
        assertThat(row.get("room_id")).isEqualTo(ROOM);
        assertThat(row.get("source_set_id")).isEqualTo("set-1");
        assertThat(row.get("provenance")).isEqualTo("REAL_USER");
        assertThat(((OffsetDateTime) toOffset(row.get("window_end"))).toInstant())
                .isEqualTo(sent.plusHours(24).atZone(clock.getZone()).toInstant());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.reply_depth_observation WHERE message_id = ?",
                Long.class, fresh)).isZero();
    }

    @Test
    @DisplayName("경로 중간이 지워졌거나 답장이 숨겨지면 depth NULL 미관측, 답글이 없으면 0, 무효 출처는 비운다")
    void givenBrokenOrHiddenOrEmpty_whenCollected_thenUnobservedOrZero() {
        long broken = message(ROOM, VIEWER, null, sent, null);
        long middle = message(ROOM, PARTNER, broken, sent.plusMinutes(1), "deleted_at");
        message(ROOM, VIEWER, middle, sent.plusMinutes(2), null);
        link(broken, sent, "VALID");
        long hidden = message(ROOM, VIEWER, null, sent.plusMinutes(10), "member_removed_at");
        link(hidden, sent.plusMinutes(10), "VALID");
        long lonely = message(ROOM, VIEWER, null, sent.plusMinutes(20), null);
        link(lonely, sent.plusMinutes(20), "INVALID");

        assertThat(service.collectDue()).isEqualTo(3);

        assertThat(observation(broken)).containsEntry("status", "UNOBSERVED")
                .containsEntry("unobserved_reason", "CHAIN_BROKEN").containsEntry("depth", null);
        assertThat(observation(hidden)).containsEntry("status", "UNOBSERVED")
                .containsEntry("unobserved_reason", "REPLY_UNAVAILABLE").containsEntry("depth", null);
        assertThat(observation(lonely)).containsEntry("status", "OBSERVED").containsEntry("depth", 0)
                .containsEntry("width", 0).containsEntry("source_set_id", null);
    }

    @Test
    @DisplayName("탈퇴 회원 파기는 답장 연결과 함께 그 사람의 Depth 라벨을 지운다")
    void givenLabel_whenViewerPurged_thenLabelRemoved() {
        long r = message(ROOM, VIEWER, null, sent, null);
        link(r, sent, "NONE");
        service.collectDue();
        assertThat(observation(r)).isNotNull();

        new TransactionTemplate(tx).executeWithoutResult(ignored ->
                purge.purgePersonalData(VIEWER, LocalDateTime.now(clock)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.reply_depth_observation WHERE viewer_id = ?",
                Long.class, VIEWER)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.reply_link WHERE viewer_id = ?",
                Long.class, VIEWER)).isZero();
    }

    @Test
    @DisplayName("노출 출처가 SYNTHETIC 인 답장이 앞에 있어도 수집이 멈추지 않고 출처를 그대로 적는다")
    void givenSyntheticEpisodeFirst_whenCollected_thenAllLabelled() {
        long synthetic = message(ROOM, VIEWER, null, sent, null);
        link(synthetic, VIEWER, 77L, sent, "VALID", episode("SYNTHETIC", sent.minusMinutes(1)));
        long real = message(ROOM, VIEWER, null, sent.plusMinutes(1), null);
        link(real, sent.plusMinutes(1), "NONE");

        assertThat(service.collectDue()).isEqualTo(2);

        assertThat(observation(synthetic)).containsEntry("provenance", "SYNTHETIC");
        assertThat(observation(real)).containsEntry("provenance", "REAL_USER");
    }

    @Test
    @DisplayName("탈퇴 회원 파기는 그 사람 영상에 다른 사람이 단 답장의 라벨도 지운다")
    void givenReplyOnWithdrawnUsersVideo_whenUploaderPurged_thenLabelRemoved() {
        LocalDateTime now = LocalDateTime.now(clock);
        long roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3))).toDomain().id();
        long uploader = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        long uploaderMember = members.save(RoomMemberEntity.from(
                new RoomMember(null, roomId, uploader, "MALE", "서연", null, now))).toDomain().id();
        long videoId = videos.save(VideoPostEntity.from(VideoPost.create(uploaderMember, sent.withMinute(0), null,
                "캡션", "https://media.test/v/" + UUID.randomUUID() + ".mp4", "https://media.test/t.jpg",
                sent))).toDomain().id();
        long onVideo = message(ROOM, VIEWER, null, sent, null);
        link(onVideo, VIEWER, videoId, sent, "NONE", null);
        long elsewhere = message(ROOM, VIEWER, null, sent.plusMinutes(1), null);
        link(elsewhere, sent.plusMinutes(1), "NONE");
        assertThat(service.collectDue()).isEqualTo(2);

        new TransactionTemplate(tx).executeWithoutResult(ignored ->
                purge.purgePersonalData(uploader, LocalDateTime.now(clock)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.reply_depth_observation WHERE video_id = ?",
                Long.class, videoId)).isZero();
        assertThat(observation(elsewhere)).containsEntry("status", "OBSERVED");
    }

    @Test
    @DisplayName("보조 지표: 작성자가 트리 안에서 답했는지와 답장한 사람·작성자의 기간 안 메시지 수(연결 무관, 답장 제외)")
    void givenKnownOwner_whenCollected_thenAuxiliaryMetricsStored() {
        LocalDateTime now = LocalDateTime.now(clock);
        long roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3))).toDomain().id();
        long owner = users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        long ownerMember = members.save(RoomMemberEntity.from(
                new RoomMember(null, roomId, owner, "MALE", "서연", null, now))).toDomain().id();
        long videoId = videos.save(VideoPostEntity.from(VideoPost.create(ownerMember, sent.withMinute(0), null,
                "캡션", "https://media.test/v/" + UUID.randomUUID() + ".mp4", "https://media.test/t.jpg",
                sent))).toDomain().id();

        long r = message(ROOM, VIEWER, null, sent, null);
        long a = message(ROOM, PARTNER, r, sent.plusMinutes(1), null);
        message(ROOM, owner, a, sent.plusMinutes(2), null);                   // 작성자가 트리 안에서 답함
        message(ROOM, VIEWER, null, sent.plusMinutes(3), null);               // 연결 없는 글도 센다
        message(ROOM, owner, null, sent.plusMinutes(4), "admin_deleted_at");  // 숨겨진 글은 세지 않음
        message(ROOM, owner, null, sent.plusHours(25), null);                 // 기간 밖
        message(OTHER_ROOM, owner, null, sent.plusMinutes(5), null);          // 다른 방
        link(r, VIEWER, videoId, sent, "NONE", null);

        long quiet = message(ROOM, VIEWER, null, sent.plusMinutes(10), null);
        message(ROOM, owner, null, sent.plusMinutes(11), null);               // 작성자 글이지만 트리 밖
        link(quiet, VIEWER, videoId, sent.plusMinutes(10), "NONE", null);

        assertThat(service.collectDue()).isEqualTo(2);

        assertThat(observation(r)).containsEntry("depth", 2).containsEntry("owner_replied", true)
                .containsEntry("followup_message_count", 4);   // 작성자 2(트리 안 답글, 트리 밖 글) + 나 2(연결 없는 글, quiet)
        assertThat(observation(quiet)).containsEntry("depth", 0).containsEntry("owner_replied", false);
        // 77 번 영상(작성자 없음)은 보조 지표를 비운다.
        long unknown = message(ROOM, VIEWER, null, sent.plusMinutes(20), null);
        link(unknown, sent.plusMinutes(20), "NONE");
        service.collectDue();
        assertThat(observation(unknown)).containsEntry("owner_replied", null)
                .containsEntry("followup_message_count", null);
    }

    private static Object toOffset(Object value) {
        return value instanceof java.sql.Timestamp t ? t.toInstant().atOffset(java.time.ZoneOffset.UTC) : value;
    }
}
