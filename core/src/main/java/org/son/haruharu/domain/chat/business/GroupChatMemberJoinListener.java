package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 방 멤버 입장 커밋 후 공통 GROUP 채팅 동기화를 실행한다. */
@Component
public class GroupChatMemberJoinListener {

    private final ChatService chatService;

    public GroupChatMemberJoinListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoomMemberJoined(RoomMemberJoinedEvent event) {
        chatService.synchronizeGroupRoom(event.roomId());
    }
}
