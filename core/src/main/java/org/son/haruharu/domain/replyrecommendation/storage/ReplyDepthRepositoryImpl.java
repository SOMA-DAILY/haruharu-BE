package org.son.haruharu.domain.replyrecommendation.storage;

import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.instant;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.ts;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

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

    public ReplyDepthRepositoryImpl(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public boolean schemaReady() {
        Long found = jdbc.sql("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE lower(table_schema) = 'ai_reply' AND lower(table_name) = 'reply_depth_observation'""")
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

    @Override
    public boolean insert(ReplyDepthObservation o) {
        return jdbc.sql("""
                        INSERT INTO ai_reply.reply_depth_observation
                            (message_id, video_id, viewer_id, room_id, source_set_id, source_card_id, width, depth,
                             status, unobserved_reason, window_start, window_end, observed_at, provenance,
                             owner_replied, followup_message_count, cohort_id)
                        VALUES (:messageId, :videoId, :viewerId, :roomId, :setId, :cardId, :width, :depth,
                                :status, :reason, :windowStart, :windowEnd, :observedAt, :provenance,
                                :ownerReplied, :followupCount, :cohortId)
                        ON CONFLICT (message_id) DO NOTHING""")
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
