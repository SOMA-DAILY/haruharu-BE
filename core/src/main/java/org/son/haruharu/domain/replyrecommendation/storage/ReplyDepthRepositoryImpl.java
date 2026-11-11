package org.son.haruharu.domain.replyrecommendation.storage;

import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.instant;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.ts;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.son.haruharu.domain.replyrecommendation.implement.ReplyBystanders;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthMessage;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthObservation;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthRepository;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthRoot;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthTarget;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Depth 라벨(#559). 표는 scripts/sql/559-reply-depth-up.sql 이 정의한다(엔티티 없음).
 * chat_messages 는 숨겨진 행(운영자 삭제·삭제·퇴장 멤버)까지 봐야 연결이 끊겼는지 알 수 있어 SQL 로 직접 읽는다.
 * chat_messages.created_at 은 서비스 시간대(KST) LocalDateTime 이다.
 */
@Repository
public class ReplyDepthRepositoryImpl implements ReplyDepthRepository {

    private static final String HIDDEN =
            "(admin_deleted_at IS NOT NULL OR deleted_at IS NOT NULL OR member_removed_at IS NOT NULL)";

    private final JdbcClient jdbc;
    private volatile boolean responseReady;

    public ReplyDepthRepositoryImpl(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /**
     * 표와 기수 칸(cohort_id)이 모두 있어야 준비된 것이다. 칸이 생기기 전에 559-up 을 적용한 DB 는 다시 적용(ADD COLUMN
     * IF NOT EXISTS)해야 한다. 칸 없이 INSERT 하면 라벨·LABEL_FAILED 마감이 모두 실패하므로 수집을 건너뛴다.
     */
    @Override
    public boolean schemaReady() {
        Long found = jdbc.sql("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE lower(table_schema) = 'ai_reply' AND lower(table_name) = 'reply_depth_observation'
                          AND lower(column_name) = 'cohort_id'""")
                .query(Long.class)
                .single();
        return found != null && found > 0;
    }

    @Override
    public List<ReplyDepthTarget> findDueTargets(Instant sentBefore, int limit) {
        return jdbc.sql("""
                        SELECT l.message_id, l.video_id, l.viewer_id, l.sent_at,
                               CASE WHEN l.source_status = 'VALID' THEN l.source_set_id END AS source_set_id,
                               CASE WHEN l.source_status = 'VALID' THEN l.source_card_id END AS source_card_id,
                               e.provenance
                        FROM ai_reply.reply_link l
                        LEFT JOIN ai_reply.reply_episode e ON e.id = l.episode_id
                        WHERE l.sent_at <= :sentBefore
                          AND NOT EXISTS (SELECT 1 FROM ai_reply.reply_depth_observation o
                                          WHERE o.message_id = l.message_id)
                        ORDER BY l.sent_at, l.message_id
                        LIMIT :limit""")
                .param("sentBefore", ts(sentBefore))
                .param("limit", limit)
                .query((rs, row) -> new ReplyDepthTarget(rs.getLong("message_id"), rs.getLong("video_id"),
                        rs.getLong("viewer_id"), instant(rs, "sent_at"), rs.getString("source_set_id"),
                        rs.getString("source_card_id"), rs.getString("provenance")))
                .list();
    }

    @Override
    public Optional<ReplyDepthRoot> findRoot(Long messageId) {
        // 기수: 채팅방(chat_rooms.room_id) → 3일 방(rooms.cohort_id). 어느 쪽이든 없으면 null.
        return jdbc.sql("SELECT m.id, m.chat_room_id, m.created_at, r.cohort_id,"
                        + " (m.admin_deleted_at IS NOT NULL OR m.deleted_at IS NOT NULL OR m.member_removed_at IS NOT NULL)"
                        + " AS hidden FROM chat_messages m"
                        + " LEFT JOIN chat_rooms c ON c.id = m.chat_room_id"
                        + " LEFT JOIN rooms r ON r.id = c.room_id"
                        + " WHERE m.id = :id")
                .param("id", messageId)
                .query((rs, row) -> new ReplyDepthRoot(rs.getLong("id"), rs.getLong("chat_room_id"),
                        rs.getObject("created_at", LocalDateTime.class), rs.getBoolean("hidden"),
                        AiReplySql.nullableLong(rs, "cohort_id")))
                .optional();
    }

    @Override
    public List<ReplyDepthMessage> findRepliesAfter(Long roomId, Long rootMessageId, LocalDateTime windowEnd) {
        return jdbc.sql("SELECT id, reply_to_message_id, sender_id, " + HIDDEN + " AS hidden FROM chat_messages"
                        + " WHERE chat_room_id = :roomId AND id > :rootId AND created_at <= :windowEnd"
                        + " AND reply_to_message_id IS NOT NULL")
                .param("roomId", roomId)
                .param("rootId", rootMessageId)
                .param("windowEnd", windowEnd)
                .query((rs, row) -> new ReplyDepthMessage(rs.getLong("id"), rs.getLong("reply_to_message_id"),
                        rs.getBoolean("hidden"), AiReplySql.nullableLong(rs, "sender_id")))
                .list();
    }

    @Override
    public Optional<Long> findVideoAuthor(Long videoId) {
        return jdbc.sql("SELECT m.user_id FROM video_posts v JOIN room_members m ON m.id = v.room_member_id"
                        + " WHERE v.id = :videoId")
                .param("videoId", videoId)
                .query(Long.class)
                .optional();
    }

    @Override
    public int countMessagesAfter(Long roomId, Long rootMessageId, LocalDateTime windowEnd,
                                  java.util.Collection<Long> senderIds) {
        Long count = jdbc.sql("SELECT count(*) FROM chat_messages"
                        + " WHERE chat_room_id = :roomId AND id > :rootId AND created_at <= :windowEnd"
                        + " AND sender_id IN (:senderIds) AND NOT " + HIDDEN)
                .param("roomId", roomId)
                .param("rootId", rootMessageId)
                .param("windowEnd", windowEnd)
                .param("senderIds", senderIds)
                .query(Long.class)
                .single();
        return count == null ? 0 : count.intValue();
    }

    /** 효과 라벨 칸(561 SQL). 한 번 확인되면 다시 묻지 않는다. */
    @Override
    public boolean responseSchemaReady() {
        if (responseReady) {
            return true;
        }
        Long found = jdbc.sql("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE lower(table_schema) = 'ai_reply' AND lower(table_name) = 'reply_depth_observation'
                          AND lower(column_name) IN ('owner_responded', 'owner_response_minutes',
                              'response_window_minutes', 'bystander_members', 'bystanders_responded')""")
                .query(Long.class)
                .single();
        responseReady = found != null && found == 5;
        return responseReady;
    }

    @Override
    public Optional<LocalDateTime> findFirstMessageAt(Long roomId, Long rootMessageId, LocalDateTime rootAt,
                                                      LocalDateTime windowEnd, Long senderId) {
        // id 순서와 저장 시각이 어긋난 행(동시 저장 등)이 답장 이전 말을 응답으로 세지 않게 시각도 본다.
        return jdbc.sql("SELECT created_at FROM chat_messages"
                        + " WHERE chat_room_id = :roomId AND id > :rootId AND created_at >= :rootAt"
                        + " AND created_at <= :windowEnd"
                        + " AND sender_id = :senderId AND NOT " + HIDDEN + " ORDER BY created_at, id LIMIT 1")
                .param("roomId", roomId)
                .param("rootId", rootMessageId)
                .param("windowEnd", windowEnd)
                .param("senderId", senderId)
                .param("rootAt", rootAt)
                .query((rs, row) -> rs.getObject(1, LocalDateTime.class))
                .optional();
    }

    @Override
    public ReplyBystanders countBystanders(Long roomId, Long rootMessageId, LocalDateTime rootAt,
                                           LocalDateTime windowEnd, java.util.Collection<Long> excluded) {
        // 채팅방 → 3일 방(chat_rooms.room_id) → 답장 시각에 멤버였던 사람(들어온 뒤, 나가기 전, 지워지지 않음).
        return jdbc.sql("""
                        SELECT count(DISTINCT m.user_id) AS members,
                               count(DISTINCT m.user_id) FILTER (WHERE EXISTS (
                                   SELECT 1 FROM chat_messages c
                                   WHERE c.chat_room_id = :roomId AND c.id > :rootId AND c.created_at >= :rootAt
                                     AND c.created_at <= :windowEnd
                                     AND c.sender_id = m.user_id
                                     AND c.admin_deleted_at IS NULL AND c.deleted_at IS NULL
                                     AND c.member_removed_at IS NULL)) AS responded
                        FROM chat_rooms cr
                        JOIN room_members m ON m.room_id = cr.room_id
                        WHERE cr.id = :roomId AND m.deleted_at IS NULL
                          AND (m.joined_at IS NULL OR m.joined_at <= :rootAt)
                          AND (m.member_removed_at IS NULL OR m.member_removed_at > :rootAt)
                          AND m.user_id NOT IN (:excluded)""")
                .param("roomId", roomId)
                .param("rootId", rootMessageId)
                .param("windowEnd", windowEnd)
                .param("rootAt", rootAt)
                .param("excluded", excluded)
                .query((rs, row) -> new ReplyBystanders(rs.getInt("members"), rs.getInt("responded")))
                .single();
    }

    @Override
    public boolean insert(ReplyDepthObservation o) {
        // 효과 라벨(이슈 #560)은 계산했을 때만(561 SQL 이 있을 때만) 칸을 쓴다.
        boolean response = o.response().present();
        String columns = response
                ? ", owner_responded, owner_response_minutes, response_window_minutes, bystander_members,"
                        + " bystanders_responded" : "";
        String values = response
                ? ", :ownerResponded, :ownerResponseMinutes, :responseWindow, :bystanders, :bystandersResponded" : "";
        var sql = jdbc.sql("""
                        INSERT INTO ai_reply.reply_depth_observation
                            (message_id, video_id, viewer_id, room_id, source_set_id, source_card_id, width, depth,
                             status, unobserved_reason, window_start, window_end, observed_at, provenance,
                             owner_replied, followup_message_count, cohort_id%s)
                        VALUES (:messageId, :videoId, :viewerId, :roomId, :setId, :cardId, :width, :depth,
                                :status, :reason, :windowStart, :windowEnd, :observedAt, :provenance,
                                :ownerReplied, :followupCount, :cohortId%s)
                        ON CONFLICT (message_id) DO NOTHING""".formatted(columns, values));
        if (response) {
            sql = sql.param("ownerResponded", o.response().ownerResponded())
                    .param("ownerResponseMinutes", o.response().ownerResponseMinutes())
                    .param("responseWindow", o.response().windowMinutes())
                    .param("bystanders", o.response().bystanderMembers())
                    .param("bystandersResponded", o.response().bystandersResponded());
        }
        return sql
                .param("messageId", o.messageId())
                .param("videoId", o.videoId())
                .param("viewerId", o.viewerId())
                .param("roomId", o.roomId())
                .param("setId", o.sourceSetId())
                .param("cardId", o.sourceCardId())
                .param("width", o.result().width())
                .param("depth", o.result().depth())
                .param("status", o.result().status().name())
                .param("reason", o.result().unobservedReason())
                .param("windowStart", ts(o.windowStart()))
                .param("windowEnd", ts(o.windowEnd()))
                .param("observedAt", ts(o.observedAt()))
                .param("provenance", o.provenance())
                .param("ownerReplied", o.ownerReplied())
                .param("followupCount", o.followupMessageCount())
                .param("cohortId", o.cohortId())
                .update() == 1;
    }
}
