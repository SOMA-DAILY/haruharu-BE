package org.son.haruharu.domain.notification.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.notification.implement.NotificationHistory;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationHistoryResponse(
        Long id,
        String type,
        String title,
        String body,
        Map<String, String> payload,
        @JsonProperty("is_read") boolean isRead,
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public static NotificationHistoryResponse from(NotificationHistory history) {
        return new NotificationHistoryResponse(
                history.id(),
                history.type().name(),
                history.title(),
                history.body(),
                history.payload(),
                history.isRead(),
                history.createdAt()
        );
    }
}
