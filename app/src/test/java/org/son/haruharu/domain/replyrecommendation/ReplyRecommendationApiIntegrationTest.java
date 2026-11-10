package org.son.haruharu.domain.replyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.block.business.BlockService;
import org.son.haruharu.domain.block.business.command.CreateBlockCommand;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.replyrecommendation.business.ReplyRecommendationMaintenanceService;
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
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.storage.VideoPostEntity;
import org.son.haruharu.domain.video.storage.VideoPostJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
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
 * 영상 답장 추천(#558) 앱 서버 흐름을 실제 PostgreSQL 과 배포 SQL 로 확인한다. 작업기(Python)가 쓰는 행은
 * 계약(docs/engineering/reply-recommendation.md)대로 테스트가 직접 넣는다.
 */
@SpringBootTest(properties = {
        "haruharu.reply-recommendation.enabled=true",
        "haruharu.reply-recommendation.ensure-rate-limit-per-minute=1000",
        "haruharu.reply-recommendation.collection.real-user-enabled=true",
        "haruharu.reply-recommendation.snapshot.initial-delay-ms=3600000",
        "haruharu.reply-recommendation.snapshot.sweep-delay-ms=3600000",
        "haruharu.reply-recommendation.maintenance-cron=-",
        "haruharu.reply-recommendation.purge-cron=-"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplyRecommendationApiIntegrationTest {

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

    /** 배포 SQL 을 먼저 적용한다. ai_reply 는 Hibernate 가 관리하지 않는다. */
    private static synchronized void applySchema() throws Exception {
        if (schemaApplied) {
            return;
        }
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("scripts/sql"))) {
            root = root.getParent();
        }
        String sql = Files.readString(root.resolve("scripts/sql/558-reply-recommendations-up.sql"));
        try (var c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var s = c.createStatement()) {
            s.execute(sql);
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
    @Autowired BlockService blocks;
    @Autowired ReplyRecommendationMaintenanceService maintenance;
    @Autowired org.son.haruharu.domain.user.implement.WithdrawnUserPurgeRepository purge;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager tx;
    @Autowired Clock clock;

    Long roomId, chatId, viewer, uploader, observer, stranger, outsider, uploaderMember, videoId;
    Long beforeMessageId, afterMessageId;

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
        var now = LocalDateTime.now(clock);
        roomId = rooms.save(RoomEntity.from(Room.createScheduled(now.minusHours(25), now.plusHours(23),
                now.plusHours(25), now.plusHours(26), now.plusHours(47), 3)
                .withReplyRecommendationEnabled(true))).toDomain().id();
        viewer = user();
        uploader = user();
        observer = user();
        stranger = user();
        outsider = user();
        member(viewer, "도윤", now);
        uploaderMember = member(uploader, "서연", now);
        member(observer, "지훈", now);
        member(stranger, "현우", now);
        chats.createGroupRoom(roomId);
        chatId = jdbc.queryForObject("SELECT id FROM chat_rooms WHERE room_id = ? AND type = 'GROUP'", Long.class, roomId);
        LocalDateTime published = now.minusMinutes(10);
        beforeMessageId = message(uploader, "내일 발표 준비해야 돼", published.minusHours(2));
        afterMessageId = message(viewer, "게시 뒤 글", published.plusMinutes(1));
        videoId = videos.save(VideoPostEntity.from(VideoPost.create(uploaderMember, published.withMinute(0), null,
                "과제 마감 D-1", "https://media.test/v/" + UUID.randomUUID() + ".mp4",
                "https://media.test/t.jpg", published))).toDomain().id();
    }

    Long user() {
        return users.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
    }

    Long member(Long userId, String name, LocalDateTime now) {
        return members.save(RoomMemberEntity.from(new RoomMember(null, roomId, userId, "MALE", name, null, now)))
                .toDomain().id();
    }

    Long message(Long sender, String content, LocalDateTime at) {
        return jdbc.queryForObject("""
                INSERT INTO chat_messages (chat_room_id, sender_id, sender_type, content, message_type, created_at,
                                           updated_at, reference_suppressed)
                VALUES (?, ?, 'USER', ?, 'TEXT', ?, ?, false) RETURNING id""",
                Long.class, chatId, sender, content, at, at);
    }

    UsernamePasswordAuthenticationToken auth(Long id) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of());
    }

    String base() {
        return "/api/v1/rooms/" + roomId + "/video-posts/" + videoId;
    }

    MvcResult ensure(Long who, int expectedStatus) throws Exception {
        return mvc.perform(post(base() + "/reply-recommendations").with(authentication(auth(who))))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    MvcResult read(Long who) throws Exception {
        return mvc.perform(get(base() + "/reply-recommendations").with(authentication(auth(who))))
                .andExpect(status().isOk()).andReturn();
    }

    static <T> T json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    void publish() {
        new TransactionTemplate(tx).executeWithoutResult(ignored ->
                events.publishEvent(new VideoUploadedEvent(videoId, roomId, uploaderMember, LocalDateTime.now(clock))));
    }

    Long analysisInputId() {
        return jdbc.queryForObject("""
                SELECT ai.id FROM ai_reply.analysis_input ai JOIN ai_reply.analysis_request ar
                  ON ar.id = ai.analysis_request_id
                WHERE ar.video_id = ? AND ar.status = 'SNAPSHOT_READY' ORDER BY ai.id DESC LIMIT 1""", Long.class, videoId);
    }

    /** 작업기가 분석을 끝낸 것처럼 결과를 넣는다. */
    Long workerAnalyzes(Long inputId) {
        Long analysisId = jdbc.queryForObject("""
                INSERT INTO ai_reply.analysis_snapshot
                    (analysis_input_id, analysis_key, video_id, scope_hash, caption_revision, media_revision,
                     history_snapshot_hash, preprocess_version, version_id, cutoff_at, input_sha, output_sha, frames,
                     output_json, current_visual_description, contextual_description, history_input_status,
                     history_requirement, history_used, model, status)
                SELECT ai.id, md5(ai.id::text || clock_timestamp()::text), ai.video_id, ai.scope_hash,
                       ai.caption_revision, ai.media_revision, ai.history_snapshot_hash, ai.preprocess_version,
                       'v1-test', ai.published_at, 'in', 'out', '[]', '{}', '책상 위 노트북과 과제 화면',
                       '책상 위 노트북과 과제 화면. 전날 발표 준비 이야기가 있었다.', 'COMPLETE', 'helpful', true,
                       'gpt-6.1-terra', 'READY'
                FROM ai_reply.analysis_input ai WHERE ai.id = ?
                RETURNING id""", Long.class, inputId);
        jdbc.update("UPDATE ai_reply.ai_job SET status = 'SUCCEEDED', finished_at = now() WHERE job_key = ?",
                "analysis:" + inputId);
        return analysisId;
    }

    /** 작업기가 일반 추천을 끝낸 것처럼 시도·세트를 넣는다. 카드는 (category, title) 쌍. */
    String workerGenerates(Long analysisId, String... categoryTitlePairs) {
        String key = "generic:" + analysisId + ":v1-test:gallery-1:r1";
        jdbc.update("""
                INSERT INTO ai_reply.ai_job (job_key, job_type, status, payload, finished_at)
                VALUES (?, 'GENERIC', 'SUCCEEDED',
                        jsonb_build_object('analysis_id', ?::bigint, 'version_id', 'v1-test',
                                           'gallery_snapshot_id', 'gallery-1', 'revision_no', 1), now())
                ON CONFLICT (job_key) DO UPDATE SET status = 'SUCCEEDED', finished_at = now()""", key, analysisId);
        String setId = UUID.randomUUID().toString();
        Long attemptId = jdbc.queryForObject("""
                INSERT INTO ai_reply.generation_attempt
                    (attempt_key, stage, analysis_id, video_id, version_id, gallery_snapshot_id, result_set_id, status)
                VALUES (?, 'GENERIC', ?, ?, 'v1-test', 'gallery-1', ?, 'SUCCEEDED') RETURNING id""",
                Long.class, key, analysisId, videoId, setId);
        var cards = new StringBuilder("[");
        for (int i = 0; i < categoryTitlePairs.length; i += 2) {
            if (i > 0) cards.append(',');
            cards.append("{\"card_id\":\"card-").append(i / 2).append("-").append(setId, 0, 8)
                    .append("\",\"category\":\"").append(categoryTitlePairs[i])
                    .append("\",\"title\":\"").append(categoryTitlePairs[i + 1]).append("\"}");
        }
        cards.append(']');
        jdbc.update("""
                INSERT INTO ai_reply.recommendation_set
                    (set_id, attempt_id, kind, analysis_id, video_id, revision_no, version_id, gallery_snapshot_id,
                     status, cards, internal_cards, expires_at)
                VALUES (?, ?, 'GENERIC', ?, ?, 1, 'v1-test', 'gallery-1', ?, CAST(? AS jsonb), '[]',
                        now() + interval '24 hours')""",
                setId, attemptId, analysisId, videoId, categoryTitlePairs.length == 0 ? "EMPTY" : "READY",
                cards.toString());
        return setId;
    }

    String readyRecommendation() throws Exception {
        publish();
        maintenance.prepareSnapshots();
        ensure(viewer, 202);
        return workerGenerates(workerAnalyzes(analysisInputId()), "question", "무슨 과제 하는지 물어볼까요?",
                "support", "과제 마무리를 응원해볼까요?");
    }

    static String ago(long seconds) {
        return java.time.Instant.now().minusSeconds(seconds).toString();
    }

    String event(String eventId, String session, String requestId, String setId, String cardId, String type,
                 String at) {
        return "{\"event_id\":\"" + eventId + "\",\"session_id\":\"" + session + "\",\"request_id\":\"" + requestId
                + "\",\"set_id\":\"" + setId + "\",\"card_id\":" + (cardId == null ? "null" : "\"" + cardId + "\"")
                + ",\"event_type\":\"" + type + "\",\"occurred_at\":\"" + at + "\"}";
    }

    @Test
    @DisplayName("게시 트랜잭션에 분석 요청이 함께 저장되고, 롤백되면 남지 않는다(outbox)")
    void givenPublish_whenCommittedOrRolledBack_thenOutboxFollowsTransaction() {
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            events.publishEvent(new VideoUploadedEvent(videoId, roomId, uploaderMember, LocalDateTime.now(clock)));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.analysis_request WHERE video_id = ?",
                Long.class, videoId)).isZero();

        publish();

        var row = jdbc.queryForMap("SELECT origin, status FROM ai_reply.analysis_request WHERE video_id = ?", videoId);
        assertThat(row).containsEntry("origin", "PUBLISH").containsEntry("status", "SNAPSHOT_PENDING");
    }

    @Test
    @DisplayName("방 칸이 꺼진 방은 게시 분석 요청·추천 요청이 생기지 않고, 칸을 켜면 같은 방에서 바로 동작한다")
    void givenRoomSwitchOff_whenPublishOrEnsure_thenNothingUntilSwitchedOn() throws Exception {
        jdbc.update("UPDATE rooms SET reply_recommendation_enabled = false WHERE id = ?", roomId);

        publish();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.analysis_request WHERE video_id = ?",
                Long.class, videoId)).isZero();
        assertThat((String) json(ensure(viewer, 200), "$.data.reason_code")).isEqualTo("FEATURE_DISABLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_request WHERE video_id = ?",
                Long.class, videoId)).isZero();

        jdbc.update("UPDATE rooms SET reply_recommendation_enabled = true WHERE id = ?", roomId);
        publish();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.analysis_request WHERE video_id = ?",
                Long.class, videoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("AI 쪽 SQL 이 실패해도 그 부분만 되돌리고 영상 게시는 저장된다(savepoint)")
    void givenAiInsertFails_whenPublish_thenVideoStillCommits() {
        jdbc.execute("ALTER TABLE ai_reply.analysis_request ADD CONSTRAINT test_block_outbox CHECK (video_id < 0) NOT VALID");
        try {
            Long saved = new TransactionTemplate(tx).execute(ignored -> {
                var now = LocalDateTime.now(clock);
                Long id = videos.save(VideoPostEntity.from(VideoPost.create(uploaderMember, now.withMinute(0), null,
                        "새 영상", "https://media.test/v/" + UUID.randomUUID() + ".mp4", null, now))).toDomain().id();
                events.publishEvent(new VideoUploadedEvent(id, roomId, uploaderMember, now));
                return id;
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM video_posts WHERE id = ?", Long.class, saved)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.analysis_request WHERE video_id = ?",
                    Long.class, saved)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE ai_reply.analysis_request DROP CONSTRAINT test_block_outbox");
        }
    }

    @Test
    @DisplayName("보관 기간이 지나면 원문 스냅샷과 분석 설명·카드·답장 원문을 지우고 해시·삭제 사실만 남긴다")
    void givenOldRecords_whenPurge_thenRawAndDerivedTextRemoved() throws Exception {
        String setId = readyRecommendation();
        var ready = ensure(viewer, 200);
        String session = UUID.randomUUID().toString();
        mvc.perform(post(base() + "/reply-recommendation-events").with(authentication(auth(viewer)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(event(UUID.randomUUID().toString(), session, json(ready, "$.data.request_id"), setId, null,
                        "impression", ago(5)))).andExpect(status().isNoContent());
        mvc.perform(post(base() + "/reply").with(authentication(auth(viewer))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"무슨 과제예요?\",\"request_id\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        for (String table : List.of("analysis_input", "analysis_snapshot", "recommendation_set", "generation_attempt")) {
            jdbc.update("UPDATE ai_reply." + table + " SET created_at = now() - interval '31 days' WHERE video_id = ?", videoId);
        }
        jdbc.update("UPDATE ai_reply.episode_reply_snapshot SET captured_at = now() - interval '31 days'");

        maintenance.purgeExpiredSnapshots();

        var input = jdbc.queryForMap("SELECT history_records::text AS records, caption, media_url, purged_at"
                + " FROM ai_reply.analysis_input WHERE video_id = ?", videoId);
        assertThat(input).containsEntry("records", "[]").containsEntry("caption", "").containsEntry("media_url", "");
        assertThat(input.get("purged_at")).isNotNull();
        var analysis = jdbc.queryForMap("SELECT status, output_json::text AS output, contextual_description"
                + " FROM ai_reply.analysis_snapshot WHERE video_id = ?", videoId);
        assertThat(analysis).containsEntry("status", "REVOKED").containsEntry("output", "{}")
                .containsEntry("contextual_description", "");
        assertThat(jdbc.queryForObject("SELECT cards::text FROM ai_reply.recommendation_set WHERE set_id = ?",
                String.class, setId)).isEqualTo("[]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.episode_reply_snapshot", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.evidence_source WHERE owner_id IN"
                + " (SELECT id FROM ai_reply.analysis_input WHERE video_id = ?)", Long.class, videoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 회원 파기는 그 사람의 시청 기록과 본인 영상에서 나온 분석·추천을 지운다")
    void givenWithdrawnUsers_whenPurged_thenTheirAiRowsAreRemoved() throws Exception {
        String setId = readyRecommendation();
        var ready = ensure(viewer, 200);
        mvc.perform(post(base() + "/reply-recommendation-events").with(authentication(auth(viewer)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(event(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                        json(ready, "$.data.request_id"), setId, null, "impression", ago(5))))
                .andExpect(status().isNoContent());

        new TransactionTemplate(tx).executeWithoutResult(ignored ->
                purge.purgePersonalData(viewer, LocalDateTime.now(clock)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_event WHERE viewer_id = ?",
                Long.class, viewer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_request WHERE viewer_id = ?",
                Long.class, viewer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.analysis_snapshot WHERE video_id = ?",
                Long.class, videoId)).isEqualTo(1);

        jdbc.update("INSERT INTO video_caption_revisions (video_post_id, previous_caption, new_caption, changed_at)"
                + " VALUES (?, '이전 캡션', '과제 마감 D-1', ?)", videoId, LocalDateTime.now(clock));
        new TransactionTemplate(tx).executeWithoutResult(ignored ->
                purge.purgePersonalData(uploader, LocalDateTime.now(clock)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM video_caption_revisions WHERE video_post_id = ?",
                Long.class, videoId)).isZero();

        for (String table : List.of("analysis_request", "analysis_input", "analysis_snapshot", "recommendation_set",
                "generation_attempt", "recommendation_request")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply." + table + " WHERE video_id = ?",
                    Long.class, videoId)).as(table).isZero();
        }
    }

    @Test
    @DisplayName("스냅샷은 게시 전 기록만 담고 분석 작업을 한 번만 넣는다(TIME01, ASYNC02)")
    void givenPendingRequest_whenPrepare_thenSnapshotHasOnlyPriorRecords() {
        publish();
        publish();

        assertThat(maintenance.prepareSnapshots()).isEqualTo(1);

        Long inputId = analysisInputId();
        String records = jdbc.queryForObject("SELECT history_records::text FROM ai_reply.analysis_input WHERE id = ?",
                String.class, inputId);
        assertThat(records).contains("chat:" + beforeMessageId).doesNotContain("chat:" + afterMessageId)
                .contains("내일 발표 준비해야 돼");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.ai_job WHERE job_key = ?", Long.class,
                "analysis:" + inputId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.evidence_source WHERE owner_id = ?",
                Long.class, inputId)).isEqualTo(1);
    }

    @Test
    @DisplayName("요청 전 조회는 NOT_REQUESTED, 준비 중 202, 작업기가 끝내면 READY 를 같은 request_id 로 돌려준다")
    void givenWorkerResults_whenEnsureAndRead_thenReadyWithSameRequestId() throws Exception {
        var none = read(viewer);
        assertThat((String) json(none, "$.data.status")).isEqualTo("UNAVAILABLE");
        assertThat((String) json(none, "$.data.request_id")).isEqualTo("none");
        assertThat((String) json(none, "$.data.reason_code")).isEqualTo("NOT_REQUESTED");

        publish();
        maintenance.prepareSnapshots();
        var pending = ensure(viewer, 202);
        String requestId = json(pending, "$.data.request_id");
        assertThat((String) json(pending, "$.data.status")).isEqualTo("PENDING");
        assertThat((Integer) json(pending, "$.data.retry_after_ms")).isPositive();
        assertThat((List<?>) json(pending, "$.data.cards")).isEmpty();
        assertThat((Object) json(pending, "$.data.set_id")).isNull();

        String setId = workerGenerates(workerAnalyzes(analysisInputId()), "question", "무슨 과제 하는지 물어볼까요?",
                "support", "과제 마무리를 응원해볼까요?");

        var ready = read(viewer);
        assertThat((String) json(ready, "$.data.status")).isEqualTo("READY");
        assertThat((String) json(ready, "$.data.request_id")).isEqualTo(requestId);
        assertThat((String) json(ready, "$.data.set_id")).isEqualTo(setId);
        assertThat((String) json(ready, "$.data.version_id")).isEqualTo("v1-test");
        assertThat((List<?>) json(ready, "$.data.cards")).hasSize(2);
        assertThat((String) json(ready, "$.data.cards[0].title")).isEqualTo("무슨 과제 하는지 물어볼까요?");
        assertThat((Object) json(ready, "$.data.retry_after_ms")).isNull();
        assertThat((Object) json(ready, "$.data.reason_code")).isNull();

        var again = ensure(viewer, 200);
        assertThat((String) json(again, "$.data.request_id")).isEqualTo(requestId);

        var shared = ensure(observer, 200);
        assertThat((String) json(shared, "$.data.status")).isEqualTo("READY");
        assertThat((String) json(shared, "$.data.set_id")).isEqualTo(setId);
        assertThat((String) json(shared, "$.data.request_id")).isNotEqualTo(requestId);
    }

    @Test
    @DisplayName("좋은 후보가 없으면 EMPTY 로 카드를 숨긴다(GEN01)")
    void givenNoCandidates_whenReady_thenEmpty() throws Exception {
        publish();
        maintenance.prepareSnapshots();
        ensure(viewer, 202);
        String setId = workerGenerates(workerAnalyzes(analysisInputId()));

        var empty = ensure(viewer, 200);
        assertThat((String) json(empty, "$.data.status")).isEqualTo("EMPTY");
        assertThat((String) json(empty, "$.data.set_id")).isEqualTo(setId);
        assertThat((List<?>) json(empty, "$.data.cards")).isEmpty();
    }

    @Test
    @DisplayName("분석이 거절·형식 위반으로 실패하면 카드 없이 UNAVAILABLE 이다(ASYNC03)")
    void givenAnalysisRefused_whenRead_thenUnavailableWithoutCards() throws Exception {
        publish();
        maintenance.prepareSnapshots();
        ensure(viewer, 202);
        jdbc.update("UPDATE ai_reply.ai_job SET status = 'FAILED', failure_code = 'REFUSAL', finished_at = now()"
                + " WHERE job_key = ?", "analysis:" + analysisInputId());

        var failed = read(viewer);
        assertThat((String) json(failed, "$.data.status")).isEqualTo("UNAVAILABLE");
        assertThat((String) json(failed, "$.data.reason_code")).isEqualTo("ANALYSIS_FAILED");
        assertThat((List<?>) json(failed, "$.data.cards")).isEmpty();
    }

    @Test
    @DisplayName("동시에 여러 번 요청해도 분석 요청·추천 요청은 하나다(ASYNC02)")
    void givenConcurrentEnsures_whenNoAnalysisYet_thenSingleRequest() throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<String>>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    gate.await();
                    var result = mvc.perform(post(base() + "/reply-recommendations").with(authentication(auth(viewer))))
                            .andReturn();
                    return JsonPath.read(result.getResponse().getContentAsString(), "$.data.request_id");
                }));
            }
            gate.countDown();
            var ids = new HashSet<String>();
            for (var future : futures) ids.add(future.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.analysis_request WHERE video_id = ?",
                Long.class, videoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_request WHERE video_id = ?",
                Long.class, videoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("사건은 본인 요청에만, 같은 내용 재전송은 204·다른 내용은 409, 형식 오류는 422 다")
    void givenEvents_whenRecorded_thenOwnershipDuplicatesAndValidation() throws Exception {
        String setId = readyRecommendation();
        var ready = ensure(viewer, 200);
        String requestId = json(ready, "$.data.request_id");
        String cardId = json(ready, "$.data.cards[0].card_id");
        String eventId = UUID.randomUUID().toString();
        String session = UUID.randomUUID().toString();
        String url = base() + "/reply-recommendation-events";
        String impression = event(eventId, session, requestId, setId, null, "impression", ago(60));

        mvc.perform(post(url).with(authentication(auth(viewer))).contentType(MediaType.APPLICATION_JSON)
                .content(impression)).andExpect(status().isNoContent());
        mvc.perform(post(url).with(authentication(auth(viewer))).contentType(MediaType.APPLICATION_JSON)
                .content(impression)).andExpect(status().isNoContent());
        mvc.perform(post(url).with(authentication(auth(viewer))).contentType(MediaType.APPLICATION_JSON)
                .content(event(eventId, session, requestId, setId, null, "impression", ago(59))))
                .andExpect(status().isConflict());
        mvc.perform(post(url).with(authentication(auth(stranger))).contentType(MediaType.APPLICATION_JSON)
                .content(event(UUID.randomUUID().toString(), session, requestId, setId, null, "impression",
                        ago(60)))).andExpect(status().isForbidden());
        mvc.perform(post(url).with(authentication(auth(viewer))).contentType(MediaType.APPLICATION_JSON)
                .content(event(UUID.randomUUID().toString(), session, requestId, setId, null, "card_selected",
                        ago(60)))).andExpect(status().is(422));
        mvc.perform(post(url).with(authentication(auth(viewer))).contentType(MediaType.APPLICATION_JSON)
                .content(event(UUID.randomUUID().toString(), session, requestId, setId, cardId, "card_selected",
                        ago(58)))).andExpect(status().isNoContent());
        mvc.perform(post(url).with(authentication(auth(viewer))).contentType(MediaType.APPLICATION_JSON)
                .content(event(UUID.randomUUID().toString(), session, requestId, setId, null, "impression",
                        java.time.Instant.now().plusSeconds(600).toString()))).andExpect(status().is(422));

        var episode = jdbc.queryForMap("SELECT first_selected_card_id, closed_at FROM ai_reply.reply_episode"
                + " WHERE session_id = ?", session);
        assertThat(episode).containsEntry("first_selected_card_id", cardId);
        assertThat(episode.get("closed_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_event WHERE session_id = ?",
                Long.class, session)).isEqualTo(2);
    }

    @Test
    @DisplayName("추천 출처가 붙은 답장은 explicit 으로 잇고, 남의 세트를 붙여도 답장은 저장되며 연결만 무효다(AUTH02)")
    void givenReplies_whenSent_thenLinkedToEpisodes() throws Exception {
        String setId = readyRecommendation();
        var ready = ensure(viewer, 200);
        String requestId = json(ready, "$.data.request_id");
        String cardId = json(ready, "$.data.cards[0].card_id");
        String session = UUID.randomUUID().toString();
        mvc.perform(post(base() + "/reply-recommendation-events").with(authentication(auth(viewer)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(event(UUID.randomUUID().toString(), session, requestId, setId, null, "impression",
                        ago(5)))).andExpect(status().isNoContent());

        String body = "{\"content\":\"무슨 과제예요?\",\"request_id\":\"" + UUID.randomUUID()
                + "\",\"recommendation_source\":{\"set_id\":\"" + setId + "\",\"card_id\":\"" + cardId
                + "\",\"session_id\":\"" + session + "\"}}";
        var reply = mvc.perform(post(base() + "/reply").with(authentication(auth(viewer)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        Number messageId = json(reply, "$.data.message_id");

        var link = jdbc.queryForMap("SELECT source_status, attribution, episode_id FROM ai_reply.reply_link"
                + " WHERE message_id = ?", messageId.longValue());
        assertThat(link).containsEntry("source_status", "VALID").containsEntry("attribution", "explicit");
        var episode = jdbc.queryForMap("SELECT outcome, attribution, actual_reply_message_id FROM ai_reply.reply_episode"
                + " WHERE session_id = ?", session);
        assertThat(episode).containsEntry("outcome", "REPLIED").containsEntry("attribution", "explicit");
        assertThat(jdbc.queryForObject("SELECT reply_text FROM ai_reply.episode_reply_snapshot WHERE message_id = ?",
                String.class, messageId.longValue())).isEqualTo("무슨 과제예요?");

        String foreign = "{\"content\":\"안녕\",\"request_id\":\"" + UUID.randomUUID()
                + "\",\"recommendation_source\":{\"set_id\":\"" + setId + "\",\"card_id\":null,\"session_id\":\"x\"}}";
        var strangerReply = mvc.perform(post(base() + "/reply").with(authentication(auth(stranger)))
                .contentType(MediaType.APPLICATION_JSON).content(foreign)).andExpect(status().isOk()).andReturn();
        Number strangerMessage = json(strangerReply, "$.data.message_id");
        var invalid = jdbc.queryForMap("SELECT source_status, invalid_reason, attribution FROM ai_reply.reply_link"
                + " WHERE message_id = ?", strangerMessage.longValue());
        assertThat(invalid).containsEntry("source_status", "INVALID").containsEntry("invalid_reason", "OTHER_VIEWER")
                .containsEntry("attribution", "unexposed");
    }

    @Test
    @DisplayName("근거 대화가 지워지면 저장된 추천을 막고 새 분석 요청을 만든다")
    void givenSourceDeleted_whenEnsure_thenInvalidatesAndRequeues() throws Exception {
        readyRecommendation();
        assertThat((String) json(ensure(viewer, 200), "$.data.status")).isEqualTo("READY");

        jdbc.update("UPDATE chat_messages SET admin_deleted_at = ? WHERE id = ?", LocalDateTime.now(clock),
                beforeMessageId);

        var again = ensure(viewer, 202);
        assertThat((String) json(again, "$.data.status")).isEqualTo("PENDING");
        assertThat(jdbc.queryForList("SELECT status FROM ai_reply.analysis_snapshot WHERE video_id = ?", String.class,
                videoId)).containsExactly("REVOKED");
        assertThat(jdbc.queryForObject("SELECT max(revision_no) FROM ai_reply.analysis_request WHERE video_id = ?",
                Integer.class, videoId)).isEqualTo(2);
        maintenance.prepareSnapshots();
        String records = jdbc.queryForObject("SELECT history_records::text FROM ai_reply.analysis_input WHERE id = ?",
                String.class, analysisInputId());
        assertThat(records).doesNotContain("chat:" + beforeMessageId);
    }

    @Test
    @DisplayName("작업기가 분석·세트를 막으면(인용한 이전 영상 삭제) 다음 요청이 새 revision 으로 다시 분석한다")
    void givenWorkerRevokedAnalysis_whenEnsure_thenReanalyzesInsteadOfStuck() throws Exception {
        String setId = readyRecommendation();
        assertThat((String) json(ensure(viewer, 200), "$.data.status")).isEqualTo("READY");
        // 작업기의 PURGE_VIDEO 가 의존 분석을 막은 상태(앱 소유인 분석 요청은 그대로 SNAPSHOT_READY)
        jdbc.update("UPDATE ai_reply.analysis_snapshot SET status = 'REVOKED', revoked_at = now() WHERE video_id = ?", videoId);
        jdbc.update("UPDATE ai_reply.recommendation_set SET revoked_at = now() WHERE set_id = ?", setId);

        var again = ensure(viewer, 202);
        assertThat((String) json(again, "$.data.status")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT max(revision_no) FROM ai_reply.analysis_request WHERE video_id = ?",
                Integer.class, videoId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.analysis_request WHERE video_id = ?"
                + " AND status = 'SNAPSHOT_PENDING'", Long.class, videoId)).isEqualTo(1);
        var stillPending = ensure(viewer, 202);
        assertThat((String) json(stillPending, "$.data.request_id")).isEqualTo(json(again, "$.data.request_id"));
    }

    @Test
    @DisplayName("캡션이 바뀌면 새 캡션으로 다시 분석하고, 업로더를 차단하면 저장된 추천도 내보내지 않는다(AUTH01)")
    void givenCaptionChangeAndBlock_whenEnsure_thenNewKeyOrUnavailable() throws Exception {
        readyRecommendation();
        jdbc.update("UPDATE video_posts SET caption = '과제 끝!' WHERE id = ?", videoId);

        assertThat((String) json(ensure(viewer, 202), "$.data.status")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT caption_revision) FROM ai_reply.analysis_request"
                + " WHERE video_id = ?", Long.class, videoId)).isEqualTo(2);

        blocks.block(viewer, new CreateBlockCommand(uploader));
        var blocked = ensure(viewer, 200);
        assertThat((String) json(blocked, "$.data.status")).isEqualTo("UNAVAILABLE");
        assertThat((String) json(blocked, "$.data.reason_code")).isEqualTo("BLOCKED");
        assertThat((String) json(read(viewer), "$.data.reason_code")).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("영상이 삭제되면 바로 UNAVAILABLE 이고, 정리 잡이 추천을 막고 파생 자료 삭제 작업을 넣는다(DEL01)")
    void givenDeletedVideo_whenEnsureAndMaintain_thenBlockedAndPurgeQueued() throws Exception {
        String setId = readyRecommendation();
        jdbc.update("UPDATE video_posts SET deleted_at = ? WHERE id = ?", LocalDateTime.now(clock), videoId);

        assertThat((String) json(ensure(viewer, 200), "$.data.reason_code")).isEqualTo("VIDEO_UNAVAILABLE");
        maintenance.maintain();

        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM ai_reply.recommendation_set WHERE set_id = ?",
                Boolean.class, setId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.ai_job WHERE job_type = 'PURGE_VIDEO'"
                + " AND payload->>'video_id' = ?", Long.class, String.valueOf(videoId))).isEqualTo(1);
    }

    @Test
    @DisplayName("본인 영상은 추천하지 않고, 단체 채팅 멤버가 아니면 403 이다")
    void givenOwnerOrOutsider_whenEnsure_thenUnavailableOrForbidden() throws Exception {
        assertThat((String) json(ensure(uploader, 200), "$.data.reason_code")).isEqualTo("OWN_VIDEO");
        ensure(outsider, 403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_reply.recommendation_request WHERE video_id = ?",
                Long.class, videoId)).isZero();
    }
}
