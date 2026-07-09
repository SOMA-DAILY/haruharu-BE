package org.son.haruharu.domain.notification.implement;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationHistoryRepository {

    void saveAll(List<NotificationHistory> histories);

    /** 최신순(id 내림차순). cursor(마지막으로 본 id)보다 작은 것부터 size개. cursor null이면 최신부터. */
    List<NotificationHistory> findByUserId(Long userId, Long cursor, int size);

    long countUnread(Long userId);

    void markAllRead(Long userId);

    int deleteOlderThan(LocalDateTime threshold);
}
