package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.matching.business.event.MatchRefundedEvent;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
public class MatchRefundedEventListener {

    private final PushSendService pushSendService;

    public MatchRefundedEventListener(PushSendService pushSendService) {
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — FCM 장애가 매칭 배치를 롤백시키지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchRefundedEvent event) {
        pushSendService.sendToUsers(event.userIds(), NotificationType.COIN_REFUNDED, Map.of());
    }
}
