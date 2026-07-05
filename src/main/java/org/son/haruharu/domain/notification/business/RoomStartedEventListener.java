package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
public class RoomStartedEventListener {

    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;

    public RoomStartedEventListener(RoomMemberReader roomMemberReader, PushSendService pushSendService) {
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — 방 활성화 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(RoomStartedEvent event) {
        List<Long> userIds = roomMemberReader.getByRoomId(event.roomId()).stream()
                .map(RoomMember::userId)
                .toList();
        pushSendService.sendToUsers(userIds, NotificationType.ROOM_STARTED,
                Map.of("roomId", event.roomId().toString()));
    }
}
