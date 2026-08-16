package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매시 정각에 SLOT_START 트리거를 스캔한다. 슬롯은 방마다 starts_at 상대 정시(slot_start)라 방마다 경계 시각이
 * 다르므로, 매시 돌며 지금이 각 방의 슬롯 시작 정각(slot_start==now)인 방에만 발송한다(SlotStartNotifier).
 * (starts_at 은 어드민이 정시로 입력하는 전제.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotBoundaryScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SlotStartNotifier slotStartNotifier;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void onSlotBoundary() {
        LocalDateTime now = LocalDateTime.now(SEOUL);
        try {
            int started = slotStartNotifier.notifyOpeningSlots(now);
            log.info("슬롯 시작 알림 완료: {}명 대상", started);
        } catch (Exception e) {
            log.error("슬롯 시작 알림 실패", e);
        }
    }
}