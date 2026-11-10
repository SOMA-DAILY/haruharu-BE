package org.son.haruharu.domain.replyrecommendation.storage;

import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.instant;
import static org.son.haruharu.domain.replyrecommendation.storage.AiReplySql.ts;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.son.haruharu.domain.replyrecommendation.implement.AiReplyJson;
import org.son.haruharu.domain.replyrecommendation.implement.Provenance;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationEvent;
import org.son.haruharu.domain.replyrecommendation.implement.RecommendationFeedbackRepository;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyAttribution;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyEpisode;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyLink;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationFeedbackRepositoryImpl implements RecommendationFeedbackRepository {

    private final JdbcClient jdbc;

    public RecommendationFeedbackRepositoryImpl(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public boolean insertEvent(RecommendationEvent event) {
        return jdbc.sql("""
                        INSERT INTO ai_reply.recommendation_event
                            (event_id, session_id, request_id, set_id, card_id, viewer_id, video_id, event_type,
                             occurred_at, received_at, late, payload_sha, provenance)
                        VALUES (:eventId, :sessionId, :requestId, :setId, :cardId, :viewerId, :videoId, :type,
                                :occurredAt, :receivedAt, :late, :sha, :provenance)
                        ON CONFLICT (event_id) DO NOTHING""")
                .param("eventId", event.eventId())
                .param("sessionId", event.sessionId())
                .param("requestId", event.requestId())
                .param("setId", event.setId())
                .param("cardId", event.cardId())
                .param("viewerId", event.viewerId())
                .param("videoId", event.videoId())
                .param("type", event.eventType())
                .param("occurredAt", ts(event.occurredAt()))
                .param("receivedAt", ts(event.receivedAt()))
                .param("late", event.late())
                .param("sha", event.payloadSha())
                .param("provenance", event.provenance().name())
                .update() == 1;
    }

    @Override
    public Optional<String> findEventPayloadSha(String eventId) {
        return jdbc.sql("SELECT payload_sha FROM ai_reply.recommendation_event WHERE event_id = :eventId")
                .param("eventId", eventId)
                .query(String.class)
                .optional();
    }

    @Override
    public void touchEpisode(RecommendationEvent event, Long roomId, Instant now) {
        boolean selected = RecommendationEvent.CARD_SELECTED.equals(event.eventType());
        boolean composing = RecommendationEvent.COMPOSER_STARTED.equals(event.eventType());
        jdbc.sql("""
                        INSERT INTO ai_reply.reply_episode
                            (session_id, set_id, request_id, viewer_id, video_id, room_id, first_impression_at,
                             last_impression_at, first_selected_card_id, composer_started_at, provenance,
                             created_at, updated_at)
                        VALUES (:sessionId, :setId, :requestId, :viewerId, :videoId, :roomId, :at, :at,
                                :selectedCard, :composerAt, :provenance, :now, :now)
                        ON CONFLICT (session_id, set_id, viewer_id, video_id) DO UPDATE SET
                            first_impression_at = LEAST(reply_episode.first_impression_at, EXCLUDED.first_impression_at),
                            last_impression_at = GREATEST(reply_episode.last_impression_at, EXCLUDED.last_impression_at),
                            first_selected_card_id = COALESCE(reply_episode.first_selected_card_id,
                                                              EXCLUDED.first_selected_card_id),
                            composer_started_at = COALESCE(reply_episode.composer_started_at,
                                                           EXCLUDED.composer_started_at),
                            updated_at = EXCLUDED.updated_at
                        WHERE reply_episode.closed_at IS NULL""")
                .param("sessionId", event.sessionId())
                .param("setId", event.setId())
                .param("requestId", event.requestId())
                .param("viewerId", event.viewerId())
                .param("videoId", event.videoId())
                .param("roomId", roomId)
                .param("at", ts(event.occurredAt()))
                .param("selectedCard", selected ? event.cardId() : null)
                .param("composerAt", composing ? ts(event.occurredAt()) : null)
                .param("provenance", event.provenance().name())
                .param("now", ts(now))
                .update();
    }

    @Override
    public List<ReplyEpisode> findOpenEpisodes(Long viewerId, Long videoId, Instant notBefore, Instant notAfter) {
        return jdbc.sql("""
                        SELECT id, session_id, set_id, viewer_id, video_id, first_impression_at, last_impression_at,
                               provenance
                        FROM ai_reply.reply_episode
                        WHERE viewer_id = :viewerId AND video_id = :videoId AND closed_at IS NULL
                          AND first_impression_at > :notBefore AND first_impression_at <= :notAfter
                        ORDER BY id""")
                .param("viewerId", viewerId)
                .param("videoId", videoId)
                .param("notBefore", ts(notBefore))
                .param("notAfter", ts(notAfter))
                .query((rs, row) -> new ReplyEpisode(rs.getLong("id"), rs.getString("session_id"),
                        rs.getString("set_id"), rs.getLong("viewer_id"), rs.getLong("video_id"),
                        instant(rs, "first_impression_at"), instant(rs, "last_impression_at"),
                        Provenance.valueOf(rs.getString("provenance"))))
                .list();
    }

    @Override
    public void closeEpisode(Long episodeId, Long messageId, Instant repliedAt, ReplyAttribution attribution,
                             Instant now) {
        jdbc.sql("""
                        UPDATE ai_reply.reply_episode
                           SET actual_reply_message_id = :messageId, actual_reply_at = :repliedAt,
                               attribution = :attribution, outcome = 'REPLIED', closed_at = :repliedAt,
                               updated_at = :now
                         WHERE id = :id AND closed_at IS NULL""")
                .param("messageId", messageId)
                .param("repliedAt", ts(repliedAt))
                .param("attribution", attribution.value())
                .param("now", ts(now))
                .param("id", episodeId)
                .update();
    }

    @Override
    public boolean insertReplyLink(ReplyLink link, Instant now) {
        return jdbc.sql("""
                        INSERT INTO ai_reply.reply_link
                            (message_id, viewer_id, video_id, sent_at, source_set_id, source_card_id,
                             source_session_id, source_status, invalid_reason, attribution, episode_id, created_at)
                        VALUES (:messageId, :viewerId, :videoId, :sentAt, :setId, :cardId, :sessionId, :status,
                                :reason, :attribution, :episodeId, :now)
                        ON CONFLICT (message_id) DO NOTHING""")
                .param("messageId", link.messageId())
                .param("viewerId", link.viewerId())
                .param("videoId", link.videoId())
                .param("sentAt", ts(link.sentAt()))
                .param("setId", link.sourceSetId())
                .param("cardId", link.sourceCardId())
                .param("sessionId", link.sourceSessionId())
                .param("status", link.sourceStatus().name())
                .param("reason", link.invalidReason())
                .param("attribution", link.attribution().value())
                .param("episodeId", link.episodeId())
                .param("now", ts(now))
                .update() == 1;
    }

    @Override
    public void saveReplySnapshot(Long episodeId, Long messageId, String text, Provenance provenance, Instant now) {
        jdbc.sql("""
                        INSERT INTO ai_reply.episode_reply_snapshot
                            (episode_id, message_id, reply_text, reply_sha, provenance, captured_at)
                        VALUES (:episodeId, :messageId, :text, :sha, :provenance, :now)
                        ON CONFLICT (episode_id) DO NOTHING""")
                .param("episodeId", episodeId)
                .param("messageId", messageId)
                .param("text", text)
                .param("sha", AiReplyJson.sha256(text))
                .param("provenance", provenance.name())
                .param("now", ts(now))
                .update();
    }

    @Override
    public int closeExpiredEpisodes(Instant openedBefore, Instant now) {
        return jdbc.sql("""
                        UPDATE ai_reply.reply_episode
                           SET outcome = 'NO_REPLY', closed_at = first_impression_at + interval '24 hours',
                               updated_at = :now
                         WHERE closed_at IS NULL AND first_impression_at <= :openedBefore""")
                .param("now", ts(now))
                .param("openedBefore", ts(openedBefore))
                .update();
    }

    @Override
    public int purgeReplySnapshotsBefore(Instant before) {
        return jdbc.sql("DELETE FROM ai_reply.episode_reply_snapshot WHERE captured_at < :before")
                .param("before", ts(before))
                .update();
    }

    @Override
    public List<Long> findReplySnapshotMessageIds(Instant since) {
        return jdbc.sql("SELECT message_id FROM ai_reply.episode_reply_snapshot WHERE captured_at >= :since")
                .param("since", ts(since))
                .query(Long.class)
                .list();
    }

    @Override
    public int deleteReplySnapshots(java.util.Collection<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql("DELETE FROM ai_reply.episode_reply_snapshot WHERE message_id IN (:ids)")
                .param("ids", messageIds)
                .update();
    }
}
