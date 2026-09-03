package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 소개팅방 시작(RoomStartedEvent) 커밋 후 GROUP 채팅을 동기화한다.
 * 방 활성화 트랜잭션이 채팅방 생성 실패로 롤백되지 않도록 AFTER_COMMIT에서 처리한다.
 */
@Component
public class GroupChatAutoCreateListener {

    private final ChatService chatService;

    public GroupChatAutoCreateListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoomStarted(RoomStartedEvent event) {
        chatService.synchronizeGroupRoom(event.roomId());
    }
}
