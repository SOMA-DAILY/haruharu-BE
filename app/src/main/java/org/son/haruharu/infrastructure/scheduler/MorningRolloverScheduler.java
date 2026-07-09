package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;
import org.son.haruharu.domain.room.business.ConsolationRewardService;
import org.son.haruharu.domain.room.business.RoomActivator;
import org.son.haruharu.domain.room.business.RoomTerminator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 09:00(하루 경계) 롤오버. 별개 @Scheduled 잡들은 단일 스레드 스케줄러에서 실행 순서가 보장되지 않아
 * (알림이 활성화보다 먼저 돌면 당일 시작 방이 첫 SLOT_START를 못 받고, 푸시 IO가 활성화를 지연시킴)
 * 순서 의존이 있는 아침 배치를 한 메서드로 통합해 순서를 코드로 강제한다(#170).
 *
 * 순서(각 단계는 독립 try-catch — 한 단계 실패가 다음 단계를 막지 않는다):
 *   1. 위로 구름 회수 지급(#143) — 22:00 발표 배치를 놓친 방의 마지막 기회(방이 ENDED 되기 전)
 *   2. 세션 종료 — 마지막 날 경과 방 ENDED 전환
 *   3. 방 활성화 — scheduledDate가 오늘이거나 지난(놓친) PENDING 방 시작
 *   4. 슬롯 시작 알림 — 방금 활성화된 방을 포함해 09시 슬롯 오픈 푸시 (12·15·18·21시는 SlotBoundaryScheduler)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MorningRolloverScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ConsolationRewardService consolationRewardService;
    private final RoomTerminator roomTerminator;
    private final RoomActivator roomActivator;
    private final SlotStartNotifier slotStartNotifier;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void rollover() {
        LocalDateTime now = LocalDateTime.now(SEOUL);
        try {
            int recovered = consolationRewardService.grantForAnnouncedRooms(now);
            if (recovered > 0) {
                log.warn("위로 구름 회수 지급: {}명 (22:00 발표 배치 누락분)", recovered);
            }
        } catch (Exception e) {
            log.error("위로 구름 회수 지급 실패", e);
        }
        try {
            int ended = roomTerminator.terminate(now, UploadSlot.dayStartHour());
            log.info("세션 종료 배치 완료: {}개 방 종료", ended);
        } catch (Exception e) {
            log.error("세션 종료 배치 실패", e);
        }
        try {
            int activated = roomActivator.activate(LocalDate.now(SEOUL), now);
            log.info("방 시작 배치 완료: {}개 방 활성화", activated);
        } catch (Exception e) {
            log.error("방 시작 배치 실패", e);
        }
        try {
            int notified = slotStartNotifier.notifyOpeningSlots(now);
            log.info("슬롯 시작 알림 완료: {}명 대상", notified);
        } catch (Exception e) {
            log.error("슬롯 시작 알림 실패", e);
        }
    }
}
