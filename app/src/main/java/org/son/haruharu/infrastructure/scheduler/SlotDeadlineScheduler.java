package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.SlotDeadlineNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매시 :30에 슬롯 마감 임박 알림을 스캔한다. 슬롯은 방마다 starts_at 상대 정시(1h 창)라, 매시 :30에 돌며
 * 지금이 각 방 현재 슬롯의 마감 30분 전(slot_start+30분==now)인 방의 미업로드 멤버에게만 보낸다(SlotDeadlineNotifier).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotDeadlineScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SlotDeadlineNotifier slotDeadlineNotifier;

    @Scheduled(cron = "0 30 * * * *", zone = "Asia/Seoul")
    public void notifyClosingSlots() {
        try {
            int notified = slotDeadlineNotifier.notifyClosingSlots(LocalDateTime.now(SEOUL));
            log.info("슬롯 마감 임박 알림 완료: {}명 대상", notified);
        } catch (Exception e) {
            log.error("슬롯 마감 임박 알림 실패", e);
        }
    }
}
