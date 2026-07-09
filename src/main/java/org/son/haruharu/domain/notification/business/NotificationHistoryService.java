package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.business.dto.NotificationsResponse;
import org.son.haruharu.domain.notification.implement.NotificationHistory;
import org.son.haruharu.domain.notification.implement.NotificationHistoryReader;
import org.son.haruharu.domain.notification.implement.NotificationHistoryWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class NotificationHistoryService {

    private final NotificationHistoryReader notificationHistoryReader;
    private final NotificationHistoryWriter notificationHistoryWriter;

    public NotificationHistoryService(NotificationHistoryReader notificationHistoryReader,
                                      NotificationHistoryWriter notificationHistoryWriter) {
        this.notificationHistoryReader = notificationHistoryReader;
        this.notificationHistoryWriter = notificationHistoryWriter;
    }

    public NotificationsResponse getNotifications(Long userId, Long cursor, int size) {
        List<NotificationHistory> items = notificationHistoryReader.getPage(userId, cursor, size);
        long unreadCount = notificationHistoryReader.countUnread(userId);
        return NotificationsResponse.of(items, unreadCount, size);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationHistoryWriter.markAllRead(userId);
    }
}
