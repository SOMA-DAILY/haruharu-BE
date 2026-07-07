package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.payment.business.event.PaymentCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
public class PaymentCompletedEventListener {

    private final PushSendService pushSendService;

    public PaymentCompletedEventListener(PushSendService pushSendService) {
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — FCM 장애가 결제 충전 처리를 롤백시키지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentCompletedEvent event) {
        pushSendService.sendToUsers(List.of(event.userId()), NotificationType.PAYMENT_COMPLETED, Map.of());
    }
}
