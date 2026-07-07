package org.son.haruharu.domain.notification.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NotificationHistoryWriter {

    private final NotificationHistoryRepository notificationHistoryRepository;

    /** inboxVisible 대상 유저 전원에게 1행씩 저장(치환 완료된 최종 title/body). */
    public void saveAll(List<Long> userIds, NotificationType type,
                        String title, String body, Map<String, String> payload) {
        List<NotificationHistory> histories = userIds.stream()
                .map(userId -> NotificationHistory.of(userId, type, title, body, payload))
                .toList();
        notificationHistoryRepository.saveAll(histories);
    }

    public void markAllRead(Long userId) {
        notificationHistoryRepository.markAllRead(userId);
    }

    public int deleteOlderThan(LocalDateTime threshold) {
        return notificationHistoryRepository.deleteOlderThan(threshold);
    }
}
