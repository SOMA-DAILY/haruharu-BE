package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.SlotDeadlineNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

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
            // 정분 반올림 필수(#421) — notifier가 slot_start+30분과 equals 비교하므로 나노초가 남으면 영원히 미발송.
            // 절삭이 아니라 반올림인 이유: cron이 경계 직전에 발화할 수 있어(실측 10:29:59.988) 절삭은 이전 분으로 떨어진다.
            int notified = slotDeadlineNotifier.notifyClosingSlots(
                    nearestMinute(LocalDateTime.now(SEOUL)));
            log.info("슬롯 마감 임박 알림 완료: {}명 대상", notified);
        } catch (Exception e) {
            log.error("슬롯 마감 임박 알림 실패", e);
        }
    }

    static LocalDateTime nearestMinute(LocalDateTime raw) {
        return raw.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES);
    }
}
