package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.FinalSelectionOpenNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 세션 마지막 날 18:05(마지막 슬롯 15~18시 종료 직후)에 최종선택 오픈 안내를 트리거한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomDayScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FinalSelectionOpenNotifier finalSelectionOpenNotifier;

    @Scheduled(cron = "0 5 18 * * *", zone = "Asia/Seoul")
    public void notifyFinalSelectionOpen() {
        try {
            int notified = finalSelectionOpenNotifier.notifyFinalSelectionOpen(LocalDateTime.now(SEOUL));
            log.info("최종선택 오픈 알림 완료: {}개 방", notified);
        } catch (Exception e) {
            log.error("최종선택 오픈 알림 실패", e);
        }
    }
}