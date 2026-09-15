package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.WinbackNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 잠수 3일째 유저에게 매칭 신청 유도(WINBACK) 알림을 트리거한다.
 * 기본 비활성(#422) — 카피·대상 선정이 폐기된 자동매칭 모델 기준이라 초대코드 모델에선 거짓 안내.
 * 재개하려면 env로 haruharu.notification.winback-cron 지정 (구 스케줄: "0 0 20 * * *").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WinbackScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WinbackNotifier winbackNotifier;

    @Scheduled(cron = "${haruharu.notification.winback-cron:-}", zone = "Asia/Seoul")
    public void notifyInactiveUsers() {
        try {
            int notified = winbackNotifier.notifyInactiveUsers(LocalDateTime.now(SEOUL));
            log.info("WINBACK 알림 완료: {}명 대상", notified);
        } catch (Exception e) {
            log.error("WINBACK 알림 실패", e);
        }
    }
}
