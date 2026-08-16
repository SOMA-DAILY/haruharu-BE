package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;

import org.son.haruharu.domain.room.business.event.FinalSelectionOpenedEvent;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 방 하나의 status 전이를 <b>독립 트랜잭션</b>으로 처리한다(#320).
 * <p>
 * reconciler 루프 안에서 try/catch 만으로는 격리가 성립하지 않는다 — {@code save()} 는 merge 라
 * 실제 UPDATE 가 <b>커밋 시점 flush 까지 지연</b>되고, 그때 터진 예외는 루프의 catch 가 잡지 못한 채
 * 트랜잭션 전체를 롤백시켜 이미 처리한 다른 방의 전이까지 되돌린다. 그래서 방 단위로 경계를 나눈다.
 * <p>
 * 부수 효과로 {@code @TransactionalEventListener(AFTER_COMMIT)} 알림도 방마다 정확한 시점에 발화한다.
 */
@Service
public class RoomStatusTransitionService {

    private final RoomWriter roomWriter;
    private final ApplicationEventPublisher eventPublisher;

    public RoomStatusTransitionService(RoomWriter roomWriter, ApplicationEventPublisher eventPublisher) {
        this.roomWriter = roomWriter;
        this.eventPublisher = eventPublisher;
    }

    /** 전이가 일어났으면 true. 파생 상태가 저장 상태와 같으면(멱등) 아무 것도 하지 않고 false. */
    @Transactional
    public boolean transition(Room room, LocalDateTime now) {
        // 스케줄 시각이 없는 구 모델 방은 09:00 배치(RoomActivator/RoomTerminator)가 담당한다.
        if (room.startsAt() == null) {
            return false;
        }
        RoomStatus derived = room.statusAt(now);
        if (derived == room.status()) {
            return false;
        }
        // materialized 가 startedAt 앵커까지 책임진다 — PENDING→SELECTING/ENDED 직행(배치 장기 중단)에도 안전.
        roomWriter.save(room.materialized(derived));
        publishTransition(room.id(), derived);
        return true;
    }

    /** 전이 알림. ENDED 는 별도 푸시가 없다(결과 공개는 result_at 기준 lazy resolve 가 담당). */
    private void publishTransition(Long roomId, RoomStatus derived) {
        if (derived == RoomStatus.ACTIVE) {
            eventPublisher.publishEvent(new RoomStartedEvent(roomId));
        } else if (derived == RoomStatus.SELECTING) {
            eventPublisher.publishEvent(new FinalSelectionOpenedEvent(roomId));
        }
    }
}
