package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.WinbackNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 매일 20:00(22:00 매칭 확정 2시간 전)에 잠수 3일째 유저에게 매칭 신청 유도(WINBACK) 알림을 트리거한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WinbackScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WinbackNotifier winbackNotifier;

    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void notifyInactiveUsers() {
        try {
            int notified = winbackNotifier.notifyInactiveUsers(LocalDateTime.now(SEOUL));
            log.info("WINBACK 알림 완료: {}명 대상", notified);
        } catch (Exception e) {
            log.error("WINBACK 알림 실패", e);
        }
    }
}
