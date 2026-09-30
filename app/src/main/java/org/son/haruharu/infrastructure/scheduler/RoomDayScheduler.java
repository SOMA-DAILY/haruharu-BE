package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.time.AdjustableClock;
import org.son.haruharu.domain.notification.business.FinalSelectionOpenNotifier;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 세션 마지막 날의 시각 트리거.
 * - 18:05(마지막 슬롯 15~18시 종료 직후): 최종선택 오픈 안내
 * - 22:00(결과 발표): 미매칭 멤버 위로 구름 지급(#143).
 *
 * 가상 시계(#180) 고정 중에는 위로 구름 배치를 skip 한다 — 실시간 크론이 backdated 테스트 방에
 * 테스트 도중 구름을 오지급하지 않게. 알림(18:05)은 상태 변경이 없어 가드하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomDayScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final FinalSelectionOpenNotifier finalSelectionOpenNotifier;
    private final ConsolationRewardService consolationRewardService;
    private final ObjectProvider<AdjustableClock> adjustableClockProvider;

    @Scheduled(cron = "0 5 18 * * *", zone = "Asia/Seoul")
    public void notifyFinalSelectionOpen() {
        try {
            int notified = finalSelectionOpenNotifier.notifyFinalSelectionOpen(LocalDateTime.now(SEOUL));
            log.info("최종선택 오픈 알림 완료: {}개 방", notified);
        } catch (Exception e) {
            log.error("최종선택 오픈 알림 실패", e);
        }
    }

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void grantConsolations() {
        AdjustableClock adjustableClock = adjustableClockProvider.getIfAvailable();
        if (adjustableClock != null && adjustableClock.isFixed()) {
            log.info("가상 시계 고정 중 — 위로 구름 배치 skip (#180)");
            return;
        }
        try {
            int granted = consolationRewardService.grantForAnnouncedRooms(LocalDateTime.now(SEOUL));
            log.info("위로 구름 지급 완료: {}명", granted);
        } catch (Exception e) {
            log.error("위로 구름 지급 실패", e);
        }
    }
}
