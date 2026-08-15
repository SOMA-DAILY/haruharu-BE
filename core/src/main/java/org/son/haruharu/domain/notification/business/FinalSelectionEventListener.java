package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
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

    // SELECTED_BY_SOMEONE(누가 널 선택함) 실시간 푸시는 제거(#310) — 새 모델은 result_at 동시공개라
    // 조기 노출을 막는다. MemberSelectedEvent 발행부는 최종선택 재설계(#311)에서 함께 정리.
    // enum 상수 SELECTED_BY_SOMEONE 은 dormant 로 보존.
}
