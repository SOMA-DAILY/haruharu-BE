package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.implement.NotificationHistoryWriter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 매일 새벽 4시, 30일 지난 알림함 항목을 정리한다(보관 기간). */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int RETENTION_DAYS = 30;

    private final NotificationHistoryWriter notificationHistoryWriter;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        try {
            LocalDateTime threshold = LocalDateTime.now(SEOUL).minusDays(RETENTION_DAYS);
            int deleted = notificationHistoryWriter.deleteOlderThan(threshold);
            log.info("알림함 정리 완료: {}건 삭제", deleted);
        } catch (Exception e) {
            log.error("알림함 정리 실패", e);
        }
    }
}
