package org.son.haruharu.domain.notification.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.son.haruharu.domain.notification.implement.NotificationHistory;

import java.util.List;

public record NotificationsResponse(
        List<NotificationHistoryResponse> notifications,
        @JsonProperty("next_cursor") Long nextCursor,
        @JsonProperty("unread_count") long unreadCount
) {
    /** items가 size만큼 꽉 찼으면 마지막 id를 다음 cursor로 넘긴다(더 있을 수 있음). */
    public static NotificationsResponse of(List<NotificationHistory> items, long unreadCount, int size) {
        List<NotificationHistoryResponse> notifications = items.stream()
                .map(NotificationHistoryResponse::from)
                .toList();
        Long nextCursor = items.size() == size ? items.get(items.size() - 1).id() : null;
        return new NotificationsResponse(notifications, nextCursor, unreadCount);
    }
}
