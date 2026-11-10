package org.son.haruharu.domain.replyrecommendation.implement;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.son.haruharu.domain.replyrecommendation.implement.exception.InvalidRecommendationEventException;

/**
 * 사건 형식 검사(event.schema.json + 명세 10장). 받은 시각보다 5분 넘게 미래인 occurred_at 은 거부하고,
 * 15분 넘게 늦게 온 사건은 late 로 표시해 실시간 지표에서 뺀다. payload_sha 로 같은 event_id 의 재전송을 가린다.
 */
public final class RecommendationEventParser {

    static final Duration MAX_FUTURE = Duration.ofMinutes(5);
    static final Duration LATE_AFTER = Duration.ofMinutes(15);
    private static final Set<String> TYPES = Set.of(RecommendationEvent.IMPRESSION, RecommendationEvent.CARD_SELECTED,
            RecommendationEvent.COMPOSER_STARTED, RecommendationEvent.DISMISSED);

    private RecommendationEventParser() {
    }

    public static RecommendationEvent parse(RawRecommendationEvent command, Long viewerId, Long videoId,
                                            Provenance provenance, Instant receivedAt) {
        String eventId = uuid(command.eventId(), "event_id");
        String sessionId = uuid(command.sessionId(), "session_id");
        String requestId = required(command.requestId(), "request_id", 36);
        String setId = required(command.setId(), "set_id", 36);
        String cardId = command.cardId() == null ? null : required(command.cardId(), "card_id", 64);
        String type = command.eventType();
        if (type == null || !TYPES.contains(type)) {
            throw new InvalidRecommendationEventException("event_type");
        }
        if (RecommendationEvent.CARD_SELECTED.equals(type) && cardId == null) {
            throw new InvalidRecommendationEventException("card_id");
        }
        Instant occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(required(command.occurredAt(), "occurred_at", 40)).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidRecommendationEventException("occurred_at");
        }
        if (occurredAt.isAfter(receivedAt.plus(MAX_FUTURE))) {
            throw new InvalidRecommendationEventException("occurred_at");
        }
        boolean late = occurredAt.isBefore(receivedAt.minus(LATE_AFTER));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", eventId);
        payload.put("session_id", sessionId);
        payload.put("request_id", requestId);
        payload.put("set_id", setId);
        payload.put("card_id", cardId);
        payload.put("event_type", type);
        payload.put("occurred_at", occurredAt.toString());
        return new RecommendationEvent(eventId, sessionId, requestId, setId, cardId, viewerId, videoId, type,
                occurredAt, receivedAt, late, AiReplyJson.sha256(AiReplyJson.write(payload)), provenance);
    }

    private static String uuid(String value, String field) {
        try {
            String normalized = required(value, field, 36).toLowerCase();
            UUID.fromString(normalized);
            if (normalized.length() != 36) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            throw new InvalidRecommendationEventException(field);
        }
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new InvalidRecommendationEventException(field);
        }
        return value;
    }
}
