package org.son.haruharu.domain.notification.business.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.notification.implement.NotificationHistory;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationHistoryResponse(
        Long id,
        @Schema(description = "알림 타입(NotificationType). 알림함에는 inboxVisible=true 타입만 저장된다",
                example = "MUTUAL_MATCH") String type,
        String title,
        String body,
        @Schema(description = "타입별 딥링크 키 — roomId(ROOM_MATCHED·ROOM_STARTED·FINAL_SELECTION_OPEN·MUTUAL_MATCH·"
                + "SELECTED_BY_SOMEONE·MATCH_CONSOLATION) / videoPostId(REACTION_RECEIVED) / chatRoomId(CHAT) / "
                + "빈 객체(COIN_REFUNDED). 값은 전부 문자열.",
                example = "{\"roomId\": \"12\"}") Map<String, String> payload,
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
