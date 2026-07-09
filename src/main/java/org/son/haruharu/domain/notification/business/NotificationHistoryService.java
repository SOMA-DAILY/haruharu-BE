package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.business.dto.NotificationsResponse;
import org.son.haruharu.domain.notification.implement.NotificationHistory;
import org.son.haruharu.domain.notification.implement.NotificationHistoryReader;
import org.son.haruharu.domain.notification.implement.NotificationHistoryWriter;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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

    /**
     * 알림함 저장 트랜잭션 경계(#158).
     *
     * 호출자 PushSendService는 발송 IO를 트랜잭션에 묶지 않으려 트랜잭션을 열지 않고,
     * 대부분 @TransactionalEventListener(AFTER_COMMIT) — 커밋된 원본 트랜잭션이 아직 바인딩된 상태 —
     * 에서 호출된다. 그 상태에서 REQUIRED로 저장하면 이미 커밋된 트랜잭션에 join되어 INSERT가 유실된다.
     * REQUIRES_NEW로 알림함 저장만 독립 커밋한다(푸시 IO는 호출자가 트랜잭션 밖에서 처리).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAll(List<Long> userIds, NotificationType type,
                        String title, String body, Map<String, String> payload) {
        notificationHistoryWriter.saveAll(userIds, type, title, body, payload);
    }
}
