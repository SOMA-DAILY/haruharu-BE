package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.SlotDeadlineNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 슬롯 종료 시각(12·15·18·21·24시) 30분 전에 마감 임박 알림을 트리거한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotDeadlineScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SlotDeadlineNotifier slotDeadlineNotifier;

    @Scheduled(cron = "0 30 11,14,17,20,23 * * *", zone = "Asia/Seoul")
    public void notifyClosingSlots() {
        try {
            int notified = slotDeadlineNotifier.notifyClosingSlots(LocalDateTime.now(SEOUL));
            log.info("슬롯 마감 임박 알림 완료: {}명 대상", notified);
        } catch (Exception e) {
            log.error("슬롯 마감 임박 알림 실패", e);
        }
    }
}
