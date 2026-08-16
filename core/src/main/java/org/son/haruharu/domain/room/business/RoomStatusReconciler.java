package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Service;

/**
 * 방 status 를 시각 기준으로 맞추는 reconciler(#320).
 * <p>
 * <b>timestamp 가 source of truth, status 는 materialized</b> 다. 유저가 보는 상태는 읽기 시점
 * {@code statusAt(now)} 로 계산되므로 이 배치가 밀려도 앱 동작은 정확하다. 배치가 하는 일은 두 가지뿐:
 * ① 조회용 저장 컬럼(admin {@code WHERE status = ?})을 따라오게 하고 ② 전이 순간 푸시를 쏜다.
 * <p>
 * <b>트랜잭션 없음(의도적):</b> 실제 전이는 {@link RoomStatusTransitionService} 가 방마다 독립 트랜잭션으로
 * 수행한다. 여기서 하나로 묶으면 한 방의 flush 실패가 이미 처리한 방들까지 롤백시킨다.
 * <p>
 * <b>멱등:</b> 파생 상태와 저장 상태가 같으면 아무 것도 하지 않는다 — 알림도 실제 전이가 일어난 방에만 나간다.
 * <p>
 * <b>한계(감수):</b> 배치가 틱을 통째로 건너뛰면(재배포 등) ACTIVE→SELECTING 을 못 보고 바로 ENDED 가 되어
 * FINAL_SELECTION_OPEN 푸시가 누락될 수 있다. 푸시는 best-effort 이고 데이터 정합성은 lazy 가 보장한다.
 */
@Slf4j
@Service
public class RoomStatusReconciler {

    private final RoomReader roomReader;
    private final RoomStatusTransitionService transitionService;

    public RoomStatusReconciler(RoomReader roomReader, RoomStatusTransitionService transitionService) {
        this.roomReader = roomReader;
        this.transitionService = transitionService;
    }

    /** 전이가 일어난 방 수를 반환한다(모니터링 로그용). */
    public int reconcile(LocalDateTime now) {
        List<Room> candidates = roomReader.getReconcilable();
        int changed = 0;
        for (Room room : candidates) {
            // 방마다 트랜잭션이 분리돼 있어 한 방의 실패가 나머지 방 전이를 되돌리지 않는다.
            try {
                if (transitionService.transition(room, now)) {
                    changed++;
                }
            } catch (RuntimeException e) {
                log.error("방 status 전이 실패: roomId={}", room.id(), e);
            }
        }
        return changed;
    }
}
