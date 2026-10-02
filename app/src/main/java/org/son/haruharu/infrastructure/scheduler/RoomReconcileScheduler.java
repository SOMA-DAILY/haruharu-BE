package org.son.haruharu.infrastructure.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.room.business.RoomStatusReconciler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 초대코드 모델 방의 status 전이 배치(#320). 5분마다 방별 시각을 훑어 저장 status 를 맞추고 전이 푸시를 쏜다.
 * <p>
 * 고정 벽시계 cron(09:00 시작·18:05 최종선택 등)과 달리 <b>방마다 시각이 달라</b> 주기 sweep 으로 처리한다.
 * 정확도는 이 배치가 아니라 읽기 시점 {@code Room.statusAt(now)} 가 보장하므로, 몇 분 밀려도 앱은 정확하다.
 * <p>
 * 나중에 Spring Batch 로 승격 예정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomReconcileScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RoomStatusReconciler roomStatusReconciler;

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    public void reconcileRooms() {
        try {
            int changed = roomStatusReconciler.reconcile(LocalDateTime.now(SEOUL));
            if (changed > 0) {
                log.info("방 status 전이 완료: {}개 방", changed);
            }
        } catch (Exception e) {
            log.error("방 status 전이 배치 실패", e);
        }
    }
}
