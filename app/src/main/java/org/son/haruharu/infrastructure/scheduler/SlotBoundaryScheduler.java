package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 슬롯 경계 정시(12·15·18·21시)에 방의 전 멤버에게 SLOT_START를 트리거한다
 * (서로의 하루 확인 + 다음 하루 남기기 유도).
 * 09시 SLOT_START는 방 활성화 "이후" 발송이 보장되어야 하므로 MorningRolloverScheduler가 담당한다(#170).
 * 21~24시 슬롯은 자정 깨우기 회피로 트리거하지 않는다(다음 슬롯 시작이 없음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotBoundaryScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SlotStartNotifier slotStartNotifier;

    @Scheduled(cron = "0 0 12,15,18,21 * * *", zone = "Asia/Seoul")
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