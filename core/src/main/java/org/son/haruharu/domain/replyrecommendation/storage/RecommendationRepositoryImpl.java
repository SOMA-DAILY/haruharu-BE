package org.son.haruharu.domain.replyrecommendation.storage;

import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.instant;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.nullableLong;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import org.son.haruharu.domain.replyrecommendation.implement.AiReplyJson;
import org.son.haruharu.domain.replyrecommendation.implement.GenerationAttemptResult;
import org.son.haruharu.domain.replyrecommendation.implement.HistoryRecord;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationCard;
import org.son.haruharu.domain.replyrecommendation.implement.EvidenceOwner;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationReason;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRepository;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationRequest;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationSet;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationRepositoryImpl implements RecommendationRepository {

    private static final TypeReference<List<RecommendationCard>> CARD_LIST = new TypeReference<>() {};

    private static final String REQUEST_COLUMNS = """
            request_id, viewer_id, video_id, room_id, analysis_request_id, version_id, personal_mode,
            personal_snapshot_hash, personal_input_id, status, reason_code, set_id, created_at, resolved_at,
            expires_at, revoked_at""";

    private static final String SET_COLUMNS = """
            set_id, kind, analysis_id, video_id, viewer_id, version_id, revision_no, status, cards::text AS cards,
            created_at, expires_at, revoked_at""";

    private final JdbcClient jdbc;

    public RecommendationRepositoryImpl(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Optional<RecommendationRequest> findRequest(String requestId) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + " FROM ai_reply.recommendation_request WHERE request_id = :id")
                .param("id", requestId)
                .query(this::request)
                .optional();
    }

    @Override
    public Optional<RecommendationRequest> findActiveRequest(Long viewerId, Long analysisRequestId, String versionId,
                                                             String personalSnapshotHash) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + """
                         FROM ai_reply.recommendation_request
                        WHERE viewer_id = :viewerId AND analysis_request_id = :analysisRequestId
                          AND version_id = :versionId AND personal_snapshot_hash = :hash AND revoked_at IS NULL""")
                .param("viewerId", viewerId)
                .param("analysisRequestId", analysisRequestId)
                .param("versionId", versionId)
                .param("hash", personalSnapshotHash)
                .query(this::request)
                .optional();
    }

    @Override
    public Optional<RecommendationRequest> findLatestRequest(Long viewerId, Long videoId) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + """
                         FROM ai_reply.recommendation_request
                        WHERE viewer_id = :viewerId AND video_id = :videoId AND revoked_at IS NULL
                        ORDER BY created_at DESC LIMIT 1""")
                .param("viewerId", viewerId)
                .param("videoId", videoId)
                .query(this::request)
                .optional();
    }

    @Override
    public RecommendationRequest insertRequest(RecommendationRequest request) {
        // 같은 키의 유효 요청이 이미 있으면(동시 요청) 유니크 인덱스에 막혀 넣지 않고 기존 요청을 쓴다.
        jdbc.sql("""
                        INSERT INTO ai_reply.recommendation_request
                            (request_id, viewer_id, video_id, room_id, analysis_request_id, version_id, personal_mode,
                             personal_snapshot_hash, status, created_at, updated_at)
                        VALUES (:requestId, :viewerId, :videoId, :roomId, :analysisRequestId, :versionId,
                                :personalMode, :hash, :status, :now, :now)
                        ON CONFLICT DO NOTHING""")
                .param("requestId", request.requestId())
                .param("viewerId", request.viewerId())
                .param("videoId", request.videoId())
                .param("roomId", request.roomId())
                .param("analysisRequestId", request.analysisRequestId())
                .param("versionId", request.versionId())
                .param("personalMode", request.personal() ? "ON" : "OFF")
                .param("hash", request.personalSnapshotHash())
                .param("status", request.status().name())
                .param("now", ts(request.createdAt()))
                .update();
        return findActiveRequest(request.viewerId(), request.analysisRequestId(), request.versionId(),
                request.personalSnapshotHash())
                .orElseThrow(() -> new IllegalStateException("추천 요청을 만들지 못했다: " + request.requestId()));
    }

    @Override
    public boolean updatePendingRequest(RecommendationRequest request, Instant now) {
        return jdbc.sql("""
                        UPDATE ai_reply.recommendation_request
                           SET analysis_request_id = :analysisRequestId, personal_input_id = :personalInputId,
                               status = :status, reason_code = :reason, set_id = :setId, resolved_at = :resolvedAt,
                               expires_at = :expiresAt, updated_at = :now
                         WHERE request_id = :requestId AND revoked_at IS NULL AND status = 'PENDING'""")
                .param("analysisRequestId", request.analysisRequestId())
                .param("personalInputId", request.personalInputId())
                .param("status", request.status().name())
                .param("reason", request.reason() == null ? null : request.reason().name())
                .param("setId", request.setId())
                .param("resolvedAt", ts(request.resolvedAt()))
                .param("expiresAt", ts(request.expiresAt()))
                .param("now", ts(now))
                .param("requestId", request.requestId())
                .update() == 1;
    }

    @Override
    public void revokeRequest(String requestId, String reason, Instant now) {
        jdbc.sql("""
                        UPDATE ai_reply.recommendation_request
                           SET revoked_at = :now, revoke_reason = :reason, updated_at = :now
                         WHERE request_id = :requestId AND revoked_at IS NULL""")
                .param("now", ts(now))
                .param("reason", reason)
                .param("requestId", requestId)
                .update();
    }

    @Override
    public Optional<RecommendationSet> findSet(String setId) {
        if (setId == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT " + SET_COLUMNS + " FROM ai_reply.recommendation_set WHERE set_id = :setId")
                .param("setId", setId)
                .query(this::set)
                .optional();
    }

    @Override
    public boolean viewerReceivedSet(Long viewerId, Long videoId, String setId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM ai_reply.recommendation_request
                                        WHERE viewer_id = :viewerId AND video_id = :videoId AND set_id = :setId)""")
                .param("viewerId", viewerId)
                .param("videoId", videoId)
                .param("setId", setId)
                .query(Boolean.class)
                .single());
    }

    @Override
    public List<RecommendationRequest> findPendingPersonalRequests(Instant since, int limit) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + """
                         FROM ai_reply.recommendation_request
                        WHERE status = 'PENDING' AND personal_mode = 'ON' AND revoked_at IS NULL
                          AND created_at >= :since
                        ORDER BY created_at
                        LIMIT :limit""")
                .param("since", ts(since))
                .param("limit", limit)
                .query(this::request)
                .list();
    }

    @Override
    public Optional<RecommendationSet> findGenericSet(Long analysisId, String versionId, String gallerySnapshotId,
                                                      int revisionNo) {
        return jdbc.sql("SELECT " + SET_COLUMNS + """
                         FROM ai_reply.recommendation_set
                        WHERE analysis_id = :analysisId AND version_id = :versionId
                          AND gallery_snapshot_id = :gallery AND kind = 'GENERIC' AND revision_no = :revision
                        ORDER BY created_at DESC LIMIT 1""")
                .param("analysisId", analysisId)
                .param("versionId", versionId)
                .param("gallery", gallerySnapshotId)
                .param("revision", revisionNo)
                .query(this::set)
                .optional();
    }

    @Override
    public Optional<GenerationAttemptResult> findAttemptResult(String attemptKey) {
        return jdbc.sql("""
                        SELECT status, failure_code, result_set_id FROM ai_reply.generation_attempt
                        WHERE attempt_key = :key""")
                .param("key", attemptKey)
                .query((rs, row) -> new GenerationAttemptResult(rs.getString("status"), rs.getString("failure_code"),
                        rs.getString("result_set_id")))
                .optional();
    }

    @Override
    public Long findOrCreatePersonalInput(Long viewerId, Long videoId, Long analysisId, String baseSetId,
                                          List<HistoryRecord> records, String personalSnapshotHash, String versionId,
                                          Instant now) {
        jdbc.sql("""
                        INSERT INTO ai_reply.personal_input
                            (viewer_id, video_id, analysis_id, base_set_id, personal_records, personal_snapshot_hash,
                             version_id, created_at)
                        VALUES (:viewerId, :videoId, :analysisId, :baseSetId, CAST(:records AS jsonb), :hash,
                                :versionId, :now)
                        ON CONFLICT (base_set_id, viewer_id, personal_snapshot_hash, version_id) DO NOTHING""")
                .param("viewerId", viewerId)
                .param("videoId", videoId)
                .param("analysisId", analysisId)
                .param("baseSetId", baseSetId)
                .param("records", AiReplyJson.write(records))
                .param("hash", personalSnapshotHash)
                .param("versionId", versionId)
                .param("now", ts(now))
                .update();
        Long id = jdbc.sql("""
                        SELECT id FROM ai_reply.personal_input
                        WHERE base_set_id = :baseSetId AND viewer_id = :viewerId AND personal_snapshot_hash = :hash
                          AND version_id = :versionId""")
                .param("baseSetId", baseSetId)
                .param("viewerId", viewerId)
                .param("hash", personalSnapshotHash)
                .param("versionId", versionId)
                .query(Long.class)
                .single();
        EvidenceSql.insert(jdbc, EvidenceOwner.PERSONAL, id, records);
        return id;
    }

    private RecommendationRequest request(ResultSet rs, int row) throws SQLException {
        String reason = rs.getString("reason_code");
        return new RecommendationRequest(rs.getString("request_id"), rs.getLong("viewer_id"), rs.getLong("video_id"),
                rs.getLong("room_id"), rs.getLong("analysis_request_id"), rs.getString("version_id"),
                "ON".equals(rs.getString("personal_mode")), rs.getString("personal_snapshot_hash"),
                nullableLong(rs, "personal_input_id"), RecommendationStatus.valueOf(rs.getString("status")),
                reason == null ? null : RecommendationReason.valueOf(reason), rs.getString("set_id"),
                instant(rs, "created_at"), instant(rs, "resolved_at"), instant(rs, "expires_at"),
                instant(rs, "revoked_at"));
    }

    private RecommendationSet set(ResultSet rs, int row) throws SQLException {
        return new RecommendationSet(rs.getString("set_id"), rs.getString("kind"), rs.getLong("analysis_id"),
                rs.getLong("video_id"), nullableLong(rs, "viewer_id"), rs.getString("version_id"),
                rs.getInt("revision_no"), rs.getString("status"), AiReplyJson.read(rs.getString("cards"), CARD_LIST),
                instant(rs, "created_at"), instant(rs, "expires_at"), instant(rs, "revoked_at"));
    }
}
