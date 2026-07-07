package org.son.haruharu.domain.notification.implement;

import java.time.LocalDateTime;
import java.util.Map;

/** 알림함 항목. 발송 시 inboxVisible 대상 유저별로 1행 저장한다(멀티캐스트 → 유저별 N행). */
public record NotificationHistory(
        Long id,
        Long userId,
        NotificationType type,
        String title,
        String body,
        Map<String, String> payload,
        boolean isRead,
        LocalDateTime createdAt
) {
    /** 신규 저장용. id·createdAt은 영속화 시점에 부여, isRead는 false로 시작. */
    public static NotificationHistory of(Long userId, NotificationType type,
                                         String title, String body, Map<String, String> payload) {
        return new NotificationHistory(null, userId, type, title, body, payload, false, null);
    }
}
