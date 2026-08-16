package org.son.haruharu.domain.notification.business;

import java.util.List;
import java.util.Map;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.business.event.FinalSelectionOpenedEvent;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 최종선택 오픈 푸시(#320). reconciler 가 방을 SELECTING 으로 전이시킨 순간 1회 발송된다.
 * 고정 cron(18:05, {@link FinalSelectionOpenNotifier})은 구 모델 방 전용으로 남는다 — 새 모델은
 * 방마다 opens_at 이 달라 벽시계 cron 으로 맞출 수 없다.
 */
@Component
public class FinalSelectionOpenedEventListener {

    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;

    public FinalSelectionOpenedEventListener(RoomMemberReader roomMemberReader, PushSendService pushSendService) {
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — 전이 트랜잭션이 푸시 실패로 롤백되지 않도록(RoomStartedEventListener 와 동일).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(FinalSelectionOpenedEvent event) {
        List<Long> userIds = roomMemberReader.getByRoomId(event.roomId()).stream()
                .map(RoomMember::userId)
                .toList();
        pushSendService.sendToUsers(userIds, NotificationType.FINAL_SELECTION_OPEN,
                Map.of("roomId", event.roomId().toString()));
    }
}
