package org.son.haruharu.domain.replyrecommendation.storage;

import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.instant;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.nullableInt;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.nullableLong;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import org.son.haruharu.domain.replyrecommendation.implement.ActiveVersion;
import org.son.haruharu.domain.replyrecommendation.implement.AiReplyJson;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisInputDraft;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisOrigin;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisPipelineRepository;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisRequest;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisRequestStatus;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisSnapshotRef;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisTarget;
import org.son.haruharu.domain.replyrecommendation.implement.EvidenceOwner;
import org.son.haruharu.domain.replyrecommendation.implement.EvidenceRef;
import org.son.haruharu.domain.replyrecommendation.implement.HistoryRecord;
import org.son.haruharu.domain.replyrecommendation.implement.JobState;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisPipelineRepositoryImpl implements AnalysisPipelineRepository {

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<HistoryRecord>> RECORD_LIST = new TypeReference<>() {};
    private static final Duration SCHEMA_RECHECK = Duration.ofMinutes(1);

    private static final String REQUEST_COLUMNS = """
            id, video_id, room_id, author_id, scope_hash, excluded_actor_ids::text AS excluded_actor_ids,
            caption_revision, media_revision, preprocess_version, revision_no, origin, status, analysis_input_id,
            attempt_count, created_at, updated_at""";

    private final JdbcClient jdbc;
    private final Clock clock;
    private volatile boolean schemaReady;
    private volatile Instant schemaCheckedAt;

    public AnalysisPipelineRepositoryImpl(DataSource dataSource, Clock clock) {
        this.jdbc = JdbcClient.create(dataSource);
        this.clock = clock;
    }

    @Override
    public Optional<ActiveVersion> findActiveVersion() {
        return jdbc.sql("""
                        SELECT a.version_id, a.revision, v.preprocess_version, v.gallery_snapshot_id
                        FROM ai_reply.active_version a
                        JOIN ai_reply.version_registry v ON v.version_id = a.version_id
                        WHERE a.scope = 'online'""")
                .query((rs, row) -> new ActiveVersion(rs.getString("version_id"), rs.getLong("revision"),
                        rs.getString("preprocess_version"), rs.getString("gallery_snapshot_id")))
                .optional();
    }

    @Override
    public Optional<ActiveVersion> findVersion(String versionId) {
        return jdbc.sql("""
                        SELECT v.version_id, COALESCE(a.revision, 0) AS revision, v.preprocess_version,
                               v.gallery_snapshot_id
                        FROM ai_reply.version_registry v
                        LEFT JOIN ai_reply.active_version a ON a.version_id = v.version_id AND a.scope = 'online'
                        WHERE v.version_id = :versionId""")
                .param("versionId", versionId)
                .query((rs, row) -> new ActiveVersion(rs.getString("version_id"), rs.getLong("revision"),
                        rs.getString("preprocess_version"), rs.getString("gallery_snapshot_id")))
                .optional();
    }

    @Override
    public boolean schemaReady() {
        Instant now = clock.instant();
        if (schemaReady || (schemaCheckedAt != null && schemaCheckedAt.plus(SCHEMA_RECHECK).isAfter(now))) {
            return schemaReady;
        }
        Boolean ready = jdbc.sql("SELECT to_regclass('ai_reply.analysis_request') IS NOT NULL")
                .query(Boolean.class).single();
        schemaReady = Boolean.TRUE.equals(ready);
        schemaCheckedAt = now;
        return schemaReady;
    }

    @Override
    public Optional<AnalysisRequest> findActiveAnalysisRequest(AnalysisTarget target) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + """
                         FROM ai_reply.analysis_request
                        WHERE video_id = :videoId AND scope_hash = :scope AND caption_revision = :caption
                          AND media_revision = :media AND preprocess_version = :preprocess AND status <> 'CANCELLED'
                        ORDER BY revision_no DESC LIMIT 1""")
                .param("videoId", target.videoId())
                .param("scope", target.scope().hash())
                .param("caption", target.captionRevision())
                .param("media", target.mediaRevision())
                .param("preprocess", target.preprocessVersion())
                .query(this::request)
                .optional();
    }

    @Override
    public Optional<AnalysisRequest> findAnalysisRequest(Long id) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + " FROM ai_reply.analysis_request WHERE id = :id")
                .param("id", id)
                .query(this::request)
                .optional();
    }

    @Override
    public AnalysisRequest createAnalysisRequest(AnalysisTarget target, AnalysisOrigin origin, Instant now) {
        Optional<AnalysisRequest> existing = findActiveAnalysisRequest(target);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 같은 키를 동시에 만들면 한쪽은 유니크 인덱스에 막혀 아무것도 넣지 않는다(트랜잭션은 깨지지 않는다).
        jdbc.sql("""
                        INSERT INTO ai_reply.analysis_request
                            (video_id, room_id, author_id, scope_hash, excluded_actor_ids, caption_revision,
                             media_revision, preprocess_version, revision_no, origin, status, next_attempt_at,
                             created_at, updated_at)
                        VALUES (:videoId, :roomId, :authorId, :scope, CAST(:excluded AS jsonb), :caption, :media,
                                :preprocess,
                                (SELECT COALESCE(MAX(revision_no), 0) + 1 FROM ai_reply.analysis_request
                                  WHERE video_id = :videoId AND scope_hash = :scope AND caption_revision = :caption
                                    AND media_revision = :media AND preprocess_version = :preprocess),
                                :origin, 'SNAPSHOT_PENDING', :now, :now, :now)
                        ON CONFLICT DO NOTHING""")
                .param("videoId", target.videoId())
                .param("roomId", target.roomId())
                .param("authorId", target.authorId())
                .param("scope", target.scope().hash())
                .param("excluded", AiReplyJson.write(target.scope().excludedActorIds()))
                .param("caption", target.captionRevision())
                .param("media", target.mediaRevision())
                .param("preprocess", target.preprocessVersion())
                .param("origin", origin.name())
                .param("now", ts(now))
                .update();
        return findActiveAnalysisRequest(target)
                .orElseThrow(() -> new IllegalStateException("분석 요청을 만들지 못했다: video=" + target.videoId()));
    }

    @Override
    public Optional<Long> claimPendingSnapshot(Instant now, Instant claimUntil) {
        return jdbc.sql("""
                        UPDATE ai_reply.analysis_request
                           SET claimed_until = :until, attempt_count = attempt_count + 1, updated_at = :now
                         WHERE id = (SELECT id FROM ai_reply.analysis_request
                                      WHERE status = 'SNAPSHOT_PENDING' AND next_attempt_at <= :now
                                        AND (claimed_until IS NULL OR claimed_until < :now)
                                      ORDER BY id
                                      LIMIT 1
                                      FOR UPDATE SKIP LOCKED)
                        RETURNING id""")
                .param("until", ts(claimUntil))
                .param("now", ts(now))
                .query(Long.class)
                .optional();
    }

    @Override
    public Long insertAnalysisInput(AnalysisInputDraft draft, Instant now) {
        AnalysisRequest request = draft.request();
        jdbc.sql("""
                        INSERT INTO ai_reply.analysis_input
                            (analysis_request_id, video_id, room_id, author_id, published_at, caption,
                             caption_revision, media_url, media_revision, scope_hash, history_records,
                             history_snapshot_hash, history_status, excluded_sources, preprocess_version, created_at)
                        VALUES (:requestId, :videoId, :roomId, :authorId, CAST(:publishedAt AS timestamptz), :caption,
                                :captionRevision, :mediaUrl, :mediaRevision, :scope, CAST(:records AS jsonb),
                                :historyHash, :historyStatus, CAST(:excluded AS jsonb), :preprocess, :now)
                        ON CONFLICT (analysis_request_id) DO NOTHING""")
                .param("requestId", request.id())
                .param("videoId", request.videoId())
                .param("roomId", request.roomId())
                .param("authorId", request.authorId())
                .param("publishedAt", draft.publishedAt())
                .param("caption", draft.caption())
                .param("captionRevision", request.captionRevision())
                .param("mediaUrl", draft.mediaUrl())
                .param("mediaRevision", request.mediaRevision())
                .param("scope", request.scopeHash())
                .param("records", AiReplyJson.write(draft.history().records()))
                .param("historyHash", draft.history().hash())
                .param("historyStatus", draft.history().status())
                .param("excluded", AiReplyJson.write(draft.history().excluded()))
                .param("preprocess", request.preprocessVersion())
                .param("now", ts(now))
                .update();
        return jdbc.sql("SELECT id FROM ai_reply.analysis_input WHERE analysis_request_id = :requestId")
                .param("requestId", request.id())
                .query(Long.class)
                .single();
    }

    @Override
    public void insertEvidence(EvidenceOwner owner, Long ownerId, List<HistoryRecord> records) {
        EvidenceSql.insert(jdbc, owner, ownerId, records);
    }

    @Override
    public void markSnapshotReady(Long analysisRequestId, Long analysisInputId, Instant now) {
        jdbc.sql("""
                        UPDATE ai_reply.analysis_request
                           SET status = 'SNAPSHOT_READY', analysis_input_id = :inputId, claimed_until = NULL,
                               failure_code = NULL, updated_at = :now
                         WHERE id = :id AND status = 'SNAPSHOT_PENDING'""")
                .param("inputId", analysisInputId)
                .param("now", ts(now))
                .param("id", analysisRequestId)
                .update();
    }

    @Override
    public void retrySnapshot(Long id, Instant nextAttemptAt, String failureCode, int maxAttempts, Instant now) {
        jdbc.sql("""
                        UPDATE ai_reply.analysis_request
                           SET status = CASE WHEN attempt_count >= :max THEN 'SNAPSHOT_FAILED' ELSE status END,
                               next_attempt_at = :next, claimed_until = NULL, failure_code = :code, updated_at = :now
                         WHERE id = :id AND status = 'SNAPSHOT_PENDING'""")
                .param("max", maxAttempts)
                .param("next", ts(nextAttemptAt))
                .param("code", failureCode)
                .param("now", ts(now))
                .param("id", id)
                .update();
    }

    @Override
    public void cancelAnalysisRequest(Long id, String reason, Instant now) {
        jdbc.sql("""
                        UPDATE ai_reply.analysis_request
                           SET status = 'CANCELLED', cancel_reason = :reason, claimed_until = NULL, updated_at = :now
                         WHERE id = :id AND status <> 'CANCELLED'""")
                .param("reason", reason)
                .param("now", ts(now))
                .param("id", id)
                .update();
    }

    @Override
    public void revokeAnalysisRequest(Long id, String reason, Instant now) {
        cancelAnalysisRequest(id, reason, now);
        revokeDerived("ar.id = :key", Map.of("key", id), reason, now);
    }

    @Override
    public int revokeVideo(Long videoId, String reason, Instant now) {
        jdbc.sql("""
                        UPDATE ai_reply.analysis_request
                           SET status = 'CANCELLED', cancel_reason = :reason, claimed_until = NULL, updated_at = :now
                         WHERE video_id = :videoId AND status <> 'CANCELLED'""")
                .param("reason", reason)
                .param("now", ts(now))
                .param("videoId", videoId)
                .update();
        return revokeDerived("ar.video_id = :key", Map.of("key", videoId), reason, now);
    }

    /** 분석 요청 조건에 걸리는 분석 결과·세트·요청을 막고, 아직 시작 안 한 작업을 취소한다. 새로 막은 분석·세트 수. */
    private int revokeDerived(String condition, Map<String, Object> params, String reason, Instant now) {
        String inputs = "SELECT ai.id FROM ai_reply.analysis_input ai JOIN ai_reply.analysis_request ar"
                + " ON ar.id = ai.analysis_request_id WHERE " + condition;
        String snapshots = "SELECT s.id FROM ai_reply.analysis_snapshot s WHERE s.analysis_input_id IN (" + inputs + ")";
        int revoked = jdbc.sql("UPDATE ai_reply.analysis_snapshot SET status = 'REVOKED', revoked_at = :now,"
                        + " revoke_reason = :reason WHERE status = 'READY' AND analysis_input_id IN (" + inputs + ")")
                .params(params).param("now", ts(now)).param("reason", reason).update();
        revoked += jdbc.sql("UPDATE ai_reply.recommendation_set SET revoked_at = :now, revoke_reason = :reason"
                        + " WHERE revoked_at IS NULL AND analysis_id IN (" + snapshots + ")")
                .params(params).param("now", ts(now)).param("reason", reason).update();
        jdbc.sql("UPDATE ai_reply.recommendation_request SET revoked_at = :now, revoke_reason = :reason,"
                        + " updated_at = :now WHERE revoked_at IS NULL AND analysis_request_id IN"
                        + " (SELECT ar.id FROM ai_reply.analysis_request ar WHERE " + condition + ")")
                .params(params).param("now", ts(now)).param("reason", reason).update();
        String personalInputs = "SELECT p.id FROM ai_reply.personal_input p WHERE p.analysis_id IN (" + snapshots + ")";
        jdbc.sql("UPDATE ai_reply.ai_job SET status = 'CANCELLED', failure_code = 'INPUT_REVOKED',"
                        + " finished_at = :now, updated_at = :now"
                        + " WHERE status IN ('QUEUED', 'RETRY_WAIT') AND ("
                        + " (job_type = 'ANALYSIS' AND (payload->>'analysis_input_id')::bigint IN (" + inputs + "))"
                        + " OR (job_type = 'GENERIC' AND (payload->>'analysis_id')::bigint IN (" + snapshots + "))"
                        + " OR (job_type = 'PERSONAL' AND (payload->>'personal_input_id')::bigint IN ("
                        + personalInputs + ")))")
                .params(params).param("now", ts(now)).update();
        return revoked;
    }

    @Override
    public Optional<JobState> findJob(String jobKey) {
        return jdbc.sql("""
                        SELECT job_key, status, failure_code, finished_at,
                               (payload->>'revision_no')::int AS revision_no
                        FROM ai_reply.ai_job WHERE job_key = :key""")
                .param("key", jobKey)
                .query(this::job)
                .optional();
    }

    @Override
    public Optional<JobState> findLatestGenericJob(Long analysisId, String versionId, String gallerySnapshotId) {
        return jdbc.sql("""
                        SELECT job_key, status, failure_code, finished_at,
                               (payload->>'revision_no')::int AS revision_no
                        FROM ai_reply.ai_job
                        WHERE job_type = 'GENERIC' AND payload->>'analysis_id' = :analysisId
                          AND payload->>'version_id' = :versionId AND payload->>'gallery_snapshot_id' = :gallery
                        ORDER BY (payload->>'revision_no')::int DESC, id DESC
                        LIMIT 1""")
                .param("analysisId", String.valueOf(analysisId))
                .param("versionId", versionId)
                .param("gallery", gallerySnapshotId)
                .query(this::job)
                .optional();
    }

    @Override
    public boolean enqueueJob(String jobKey, String jobType, String payloadJson, int priority, Instant now) {
        return jdbc.sql("""
                        INSERT INTO ai_reply.ai_job
                            (job_key, job_type, status, payload, priority, next_attempt_at, created_at, updated_at)
                        VALUES (:key, :type, 'QUEUED', CAST(:payload AS jsonb), :priority, :now, :now, :now)
                        ON CONFLICT (job_key) DO NOTHING""")
                .param("key", jobKey)
                .param("type", jobType)
                .param("payload", payloadJson)
                .param("priority", priority)
                .param("now", ts(now))
                .update() == 1;
    }

    @Override
    public Optional<AnalysisSnapshotRef> findAnalysisSnapshotByInput(Long analysisInputId) {
        if (analysisInputId == null) {
            return Optional.empty();
        }
        return jdbc.sql("""
                        SELECT id, analysis_input_id, video_id, status
                        FROM ai_reply.analysis_snapshot WHERE analysis_input_id = :inputId""")
                .param("inputId", analysisInputId)
                .query((rs, row) -> new AnalysisSnapshotRef(rs.getLong("id"), rs.getLong("analysis_input_id"),
                        rs.getLong("video_id"), rs.getString("status")))
                .optional();
    }

    @Override
    public Map<Long, String> findLatestVisualDescriptions(Collection<Long> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        jdbc.sql("""
                        SELECT DISTINCT ON (video_id) video_id, current_visual_description
                        FROM ai_reply.analysis_snapshot
                        WHERE video_id IN (:ids) AND status = 'READY'
                        ORDER BY video_id, id DESC""")
                .param("ids", videoIds)
                .query(rs -> {
                    result.put(rs.getLong("video_id"), rs.getString("current_visual_description"));
                });
        return result;
    }

    @Override
    public List<EvidenceRef> findEvidence(EvidenceOwner owner, Long ownerId) {
        return jdbc.sql("""
                        SELECT source_kind, source_ref_id FROM ai_reply.evidence_source
                        WHERE owner_kind = :kind AND owner_id = :ownerId""")
                .param("kind", owner.name())
                .param("ownerId", ownerId)
                .query((rs, row) -> new EvidenceRef(rs.getString("source_kind"), rs.getLong("source_ref_id")))
                .list();
    }

    @Override
    public List<HistoryRecord> findAnalysisInputRecords(Long analysisInputId) {
        if (analysisInputId == null) {
            return List.of();
        }
        return jdbc.sql("SELECT history_records::text FROM ai_reply.analysis_input WHERE id = :id")
                .param("id", analysisInputId)
                .query(String.class)
                .optional()
                .map(json -> AiReplyJson.read(json, RECORD_LIST))
                .orElse(List.of());
    }

    @Override
    public List<Long> findRecentlyAnalyzedVideoIds(Instant since) {
        return jdbc.sql("""
                        SELECT DISTINCT video_id FROM ai_reply.analysis_snapshot
                        WHERE status = 'READY' AND created_at >= :since""")
                .param("since", ts(since))
                .query(Long.class)
                .list();
    }

    @Override
    public int purgeSnapshotsBefore(Instant before, Instant now) {
        int purged = 0;
        // 방 기록·캡션·원본 주소(원문 스냅샷).
        purged += jdbc.sql("""
                        UPDATE ai_reply.analysis_input
                           SET history_records = '[]'::jsonb, caption = '', media_url = '', purged_at = :now
                         WHERE created_at < :before AND purged_at IS NULL""")
                .param("now", ts(now)).param("before", ts(before)).update();
        purged += jdbc.sql("""
                        UPDATE ai_reply.personal_input SET personal_records = '[]'::jsonb, purged_at = :now
                         WHERE created_at < :before AND purged_at IS NULL""")
                .param("now", ts(now)).param("before", ts(before)).update();
        // 원문에서 나온 파생 자료: 분석 설명·인용, 후보·검수, 카드 문구. 상태는 무효로 남긴다.
        purged += jdbc.sql("""
                        UPDATE ai_reply.analysis_snapshot
                           SET output_json = '{}'::jsonb, current_visual_description = '', contextual_description = '',
                               frames = '[]'::jsonb, status = 'REVOKED', revoked_at = COALESCE(revoked_at, :now),
                               revoke_reason = COALESCE(revoke_reason, 'RETENTION')
                         WHERE created_at < :before
                           AND (output_json <> '{}'::jsonb OR current_visual_description <> ''
                                OR contextual_description <> '' OR frames <> '[]'::jsonb)""")
                .param("now", ts(now)).param("before", ts(before)).update();
        purged += jdbc.sql("""
                        UPDATE ai_reply.generation_attempt SET candidates = NULL, gate = NULL, retrieval = NULL
                         WHERE created_at < :before AND (candidates IS NOT NULL OR gate IS NOT NULL OR retrieval IS NOT NULL)""")
                .param("before", ts(before)).update();
        // 피드백 판정 근거·동결 검증 세트(실제 답장 원문·카드 문구가 들어 있다).
        purged += jdbc.sql("""
                        UPDATE ai_reply.feedback_judgment SET output_json = '{}'::jsonb
                         WHERE created_at < :before AND output_json <> '{}'::jsonb""")
                .param("before", ts(before)).update();
        purged += jdbc.sql("""
                        UPDATE ai_reply.validation_run SET suite = '[]'::jsonb, frozen_outputs = NULL
                         WHERE created_at < :before AND (suite <> '[]'::jsonb OR frozen_outputs IS NOT NULL)""")
                .param("before", ts(before)).update();
        purged += jdbc.sql("""
                        UPDATE ai_reply.recommendation_set
                           SET cards = '[]'::jsonb, internal_cards = '[]'::jsonb, revoked_at = COALESCE(revoked_at, :now),
                               revoke_reason = COALESCE(revoke_reason, 'RETENTION')
                         WHERE created_at < :before AND (cards <> '[]'::jsonb OR internal_cards <> '[]'::jsonb)""")
                .param("now", ts(now)).param("before", ts(before)).update();
        return purged;
    }

    private AnalysisRequest request(ResultSet rs, int row) throws SQLException {
        return new AnalysisRequest(rs.getLong("id"), rs.getLong("video_id"), rs.getLong("room_id"),
                rs.getLong("author_id"), rs.getString("scope_hash"),
                AiReplyJson.read(rs.getString("excluded_actor_ids"), LONG_LIST), rs.getString("caption_revision"),
                rs.getString("media_revision"), rs.getString("preprocess_version"), rs.getInt("revision_no"),
                AnalysisOrigin.valueOf(rs.getString("origin")), AnalysisRequestStatus.valueOf(rs.getString("status")),
                nullableLong(rs, "analysis_input_id"), rs.getInt("attempt_count"), instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private JobState job(ResultSet rs, int row) throws SQLException {
        return new JobState(rs.getString("job_key"), rs.getString("status"), rs.getString("failure_code"),
                instant(rs, "finished_at"), nullableInt(rs, "revision_no"));
    }
}
