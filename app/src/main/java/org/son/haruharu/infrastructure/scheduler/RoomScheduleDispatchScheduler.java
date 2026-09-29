package org.son.haruharu.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.roomschedule.business.RoomScheduleDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 방 편성 발송 잡(#457). 매분 20초 — 미션 배정 0초, 슬롯 알림 15초, outcome 30초와 겹치지 않게.
 * 플래그 haruharu.chat.admin-message.enabled 가 true 일 때만 빈이 뜬다(기본 OFF, 앱 배포 뒤 켬).
 * 인스턴스가 여러 개여도 행 단위 claim(조건부 UPDATE)으로 한 번만 보낸다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "haruharu.chat.admin-message", name = "enabled", havingValue = "true")
public class RoomScheduleDispatchScheduler {

    private final RoomScheduleDispatchService dispatchService;

    public RoomScheduleDispatchScheduler(RoomScheduleDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(cron = "20 * * * * *", zone = "Asia/Seoul")
    public void dispatch() {
        try {
            int sent = dispatchService.dispatchDue();
            if (sent > 0) {
                log.info("방 편성 발송: {}건", sent);
            }
        } catch (Exception e) {
            log.error("방 편성 발송 잡 실패", e);
        }
    }
}
