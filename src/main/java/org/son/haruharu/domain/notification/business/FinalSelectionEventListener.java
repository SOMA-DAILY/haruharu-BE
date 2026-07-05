package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.business.event.MemberSelectedEvent;
import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
public class FinalSelectionEventListener {

    private final PushSendService pushSendService;

    public FinalSelectionEventListener(PushSendService pushSendService) {
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — 최종선택 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMutualMatch(MutualMatchEvent event) {
        pushSendService.sendToUsers(List.of(event.userIdA(), event.userIdB()),
                NotificationType.MUTUAL_MATCH, Map.of("roomId", event.roomId().toString()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMemberSelected(MemberSelectedEvent event) {
        pushSendService.sendToUsers(List.of(event.selectedUserId()),
                NotificationType.SELECTED_BY_SOMEONE, Map.of("roomId", event.roomId().toString()));
    }
}
