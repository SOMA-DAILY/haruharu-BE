package org.son.haruharu.domain.matching.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.matching.implement.MatchingQueue;

import java.time.LocalDateTime;

/** 내 매칭 큐 상태. MATCHED는 클라이언트가 GET /rooms/me로 확인하므로 QUEUED | NONE만 응답한다. */
public record QueueStatusResponse(
        String status,
        @JsonProperty("queued_at") LocalDateTime queuedAt
) {
    public static QueueStatusResponse queued(MatchingQueue queue) {
        return new QueueStatusResponse("QUEUED", queue.queuedAt());
    }

    public static QueueStatusResponse none() {
        return new QueueStatusResponse("NONE", null);
    }
}
