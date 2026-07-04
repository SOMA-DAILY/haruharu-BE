package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.NewVideoNotifier;
import org.son.haruharu.domain.notification.business.SlotStartNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 슬롯 경계 정시(9·12·15·18·21시)에 두 알림을 트리거한다: 새로 열린 슬롯 미업로드자에게 SLOT_START,
 * 직전 슬롯에 새 영상이 있으면 방의 다른 멤버에게 NEW_VIDEO_POSTED.
 * Day1 09시는 방 활성화 시점이라 ROOM_STARTED와 SLOT_START가 함께 나간다(슬롯 시작 안내는 매일 필요).
 * 21~24시 슬롯은 자정 깨우기 회피로 트리거하지 않는다(다음 슬롯 시작이 없음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotBoundaryScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SlotStartNotifier slotStartNotifier;
    private final NewVideoNotifier newVideoNotifier;

    @Scheduled(cron = "0 0 9,12,15,18,21 * * *", zone = "Asia/Seoul")
    public void onSlotBoundary() {
        LocalDateTime now = LocalDateTime.now(SEOUL);
        try {
            int started = slotStartNotifier.notifyOpeningSlots(now);
            log.info("슬롯 시작 알림 완료: {}명 대상", started);
        } catch (Exception e) {
            log.error("슬롯 시작 알림 실패", e);
        }
        try {
            int newVideo = newVideoNotifier.notifyNewVideos(now);
            log.info("새 영상 알림 완료: {}명 대상", newVideo);
        } catch (Exception e) {
            log.error("새 영상 알림 실패", e);
        }
    }
}