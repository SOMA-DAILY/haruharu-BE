package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.time.AdjustableClock;
import org.son.haruharu.domain.matching.business.MatchingExecutor;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.son.haruharu.domain.room.business.RoomTerminator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매칭/방 시작 트리거. 실제 로직은 executor/activator 유스케이스에 있고, 여기서는 시각에 맞춰 호출만 한다.
 * - 22:00: 대기 큐에서 남3·여3을 묶어 방 확정(scheduledDate = 익일). 확정 즉시 RoomMatchedEvent로 결과 푸시 발송
 * - 09:00: 마지막 날 경과 방을 ENDED로 종료 후, 당일 시작 예정 방을 ACTIVE로 전환
 * cron의 zone과 본문의 now() 기준 타임존을 모두 Asia/Seoul로 고정해 날짜 계산이 어긋나지 않게 한다.
 *
 * 가상 시계(#180, local·dev 전용 AdjustableClock) 고정 중에는 방 시작·종료·위로 구름 배치를 skip 한다
 * — 실시간 크론이 고정된 테스트 상태(backdated 방)를 조기 종료·오지급으로 훼손하지 않게.
 * 매칭 확정(22:00)은 큐 기반이라 dev 방과 무관하므로 가드하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MatchingExecutor matchingExecutor;
    private final RoomActivator roomActivator;
    private final RoomTerminator roomTerminator;
    private final ConsolationRewardService consolationRewardService;
    private final ObjectProvider<AdjustableClock> adjustableClockProvider;

    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void runMatching() {
        try {
            int rooms = matchingExecutor.execute(LocalDate.now(SEOUL).plusDays(1), LocalDateTime.now(SEOUL));
            log.info("매칭 배치 완료: {}개 방 확정", rooms);
        } catch (Exception e) {
            log.error("매칭 배치 실패", e);
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void terminateSessions() {
        if (isVirtualTimeActive()) {
            log.info("가상 시계 고정 중 — 세션 종료 배치 skip (#180)");
            return;
        }
        // 종료(ENDED) 전에 위로 구름 미지급분을 회수 지급한다(#143) — 22:00 발표 배치를 놓친 방의 마지막 기회.
        // 지급은 원장 멱등이라 정상 지급된 방에는 아무 일도 일어나지 않는다.
        try {
            int recovered = consolationRewardService.grantForAnnouncedRooms(LocalDateTime.now(SEOUL));
            if (recovered > 0) {
                log.warn("위로 구름 회수 지급: {}명 (22:00 발표 배치 누락분)", recovered);
            }
        } catch (Exception e) {
            log.error("위로 구름 회수 지급 실패", e);
        }
        try {
            int ended = roomTerminator.terminate(LocalDateTime.now(SEOUL), UploadSlot.dayStartHour());
            log.info("세션 종료 배치 완료: {}개 방 종료", ended);
        } catch (Exception e) {
            log.error("세션 종료 배치 실패", e);
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void activateRooms() {
        if (isVirtualTimeActive()) {
            log.info("가상 시계 고정 중 — 방 시작 배치 skip (#180)");
            return;
        }
        try {
            int activated = roomActivator.activate(LocalDate.now(SEOUL), LocalDateTime.now(SEOUL));
            log.info("방 시작 배치 완료: {}개 방 활성화", activated);
        } catch (Exception e) {
            log.error("방 시작 배치 실패", e);
        }
    }

    private boolean isVirtualTimeActive() {
        AdjustableClock clock = adjustableClockProvider.getIfAvailable();
        return clock != null && clock.isFixed();
    }
}
