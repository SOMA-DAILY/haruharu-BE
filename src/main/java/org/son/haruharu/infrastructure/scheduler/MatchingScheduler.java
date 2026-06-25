package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.matching.business.MatchingExecutor;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매칭/방 시작 트리거. 실제 로직은 executor/activator 유스케이스에 있고, 여기서는 시각에 맞춰 호출만 한다.
 * - 00:00: 대기 큐에서 남3·여3을 묶어 방 확정(scheduledDate = 당일)
 * - 09:00: 당일 시작 예정 방을 ACTIVE로 전환
 * cron의 zone과 본문의 now() 기준 타임존을 모두 Asia/Seoul로 고정해 날짜 계산이 어긋나지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MatchingExecutor matchingExecutor;
    private final RoomActivator roomActivator;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void runMatching() {
        try {
            int rooms = matchingExecutor.execute(LocalDate.now(SEOUL), LocalDateTime.now(SEOUL));
            log.info("매칭 배치 완료: {}개 방 확정", rooms);
        } catch (Exception e) {
            log.error("매칭 배치 실패", e);
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void activateRooms() {
        try {
            int activated = roomActivator.activate(LocalDate.now(SEOUL), LocalDateTime.now(SEOUL));
            log.info("방 시작 배치 완료: {}개 방 활성화", activated);
        } catch (Exception e) {
            log.error("방 시작 배치 실패", e);
        }
    }
}
