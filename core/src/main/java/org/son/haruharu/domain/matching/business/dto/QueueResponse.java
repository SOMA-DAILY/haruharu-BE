package org.son.haruharu.domain.matching.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.matching.implement.MatchingQueue;

import java.time.LocalDateTime;

public record QueueResponse(
        @JsonProperty("queue_id") Long queueId,
        String status,
        String gender,
        @JsonProperty("queued_at") LocalDateTime queuedAt
) {
    public static QueueResponse from(MatchingQueue queue) {
        return new QueueResponse(queue.id(), queue.status().name(), queue.gender(), queue.queuedAt());
    }
}
