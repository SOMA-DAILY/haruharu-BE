package org.son.haruharu.domain.room.business;

import java.time.LocalDateTime;

import org.son.haruharu.domain.room.business.event.FinalSelectionOpenedEvent;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.business.event.RoomEndedEvent;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
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

    private final RoomReader roomReader;
    private final RoomWriter roomWriter;
    private final ApplicationEventPublisher eventPublisher;

    public RoomStatusTransitionService(RoomReader roomReader, RoomWriter roomWriter,
                                       ApplicationEventPublisher eventPublisher) {
        this.roomReader = roomReader;
        this.roomWriter = roomWriter;
        this.eventPublisher = eventPublisher;
    }

    /** 전이가 일어났으면 true. 파생 상태가 저장 상태와 같으면(멱등) 아무 것도 하지 않고 false. */
    @Transactional
    public boolean transition(Room candidate, LocalDateTime now) {
        Room room = roomReader.findByIdForUpdate(candidate.id()).orElse(null);
        if (room == null || room.status() == RoomStatus.CANCELLED || room.status() == RoomStatus.ENDED) {
            return false;
        }
        // 스케줄 시각이 없는 구 모델 방은 자동 상태 전이 대상이 아니다.
        if (room.startsAt() == null) {
            return false;
        }
        RoomStatus derived = room.statusAt(now);
        if (derived == room.status()) {
            return false;
        }
        // materialized 가 startedAt 앵커까지 책임진다 — PENDING→SELECTING/ENDED 직행(배치 장기 중단)에도 안전.
        roomWriter.save(room.materialized(derived));
        publishTransition(room.id(), room.status(), derived);
        return true;
    }

    /**
     * 전이 알림. ENDED는 GROUP 실시간 채팅 종료를 알린다.
     * <p>
     * "방이 시작됐다"(RoomStartedEvent)는 GROUP 채팅 생성의 훅이므로, 배치 장기 중단으로
     * PENDING 이 ACTIVE 를 <b>건너뛰고</b> SELECTING/ENDED 로 직행해도 한 번은 발행해야 한다
     * (안 그러면 그 방은 채팅방이 영영 안 생긴다). PENDING 에서 처음 벗어날 때를 시작 시점으로 본다.
     */
    private void publishTransition(Long roomId, RoomStatus previous, RoomStatus derived) {
        if (previous == RoomStatus.PENDING && derived != RoomStatus.CANCELLED) {
            eventPublisher.publishEvent(new RoomStartedEvent(roomId));
        }
        if (derived == RoomStatus.SELECTING) {
            eventPublisher.publishEvent(new FinalSelectionOpenedEvent(roomId));
        }
        if (derived == RoomStatus.ENDED) {
            eventPublisher.publishEvent(new RoomEndedEvent(roomId));
        }
    }
}
