package org.son.haruharu.domain.room.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 앱에 방문하지 않은 방도 기존 시작 이벤트에서 배정하여 방문 여부로 실험 대상을 고르지 않는다. */
@Component
@RequiredArgsConstructor
public class InteractionGuidanceEnrollmentListener {
    private final InteractionGuidanceService service;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void started(RoomStartedEvent event) {
        service.enroll(event.roomId());
    }
}
