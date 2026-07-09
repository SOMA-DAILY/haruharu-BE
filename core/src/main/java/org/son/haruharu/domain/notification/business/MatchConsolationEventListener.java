package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.business.event.MatchConsolationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
public class MatchConsolationEventListener {

    private final PushSendService pushSendService;

    public MatchConsolationEventListener(PushSendService pushSendService) {
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — FCM 장애가 위로 구름 지급 트랜잭션을 롤백시키지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MatchConsolationEvent event) {
        pushSendService.sendToUsers(event.userIds(), NotificationType.MATCH_CONSOLATION,
                Map.of("roomId", event.roomId().toString()), event.amount());
    }
}
