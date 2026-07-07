package org.son.haruharu.domain.notification.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationHistoryReader {

    private final NotificationHistoryRepository notificationHistoryRepository;

    public List<NotificationHistory> getPage(Long userId, Long cursor, int size) {
        return notificationHistoryRepository.findByUserId(userId, cursor, size);
    }

    public long countUnread(Long userId) {
        return notificationHistoryRepository.countUnread(userId);
    }
}
