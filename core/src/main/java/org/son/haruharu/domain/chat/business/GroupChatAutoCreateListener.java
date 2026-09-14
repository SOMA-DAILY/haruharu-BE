package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 소개팅방 시작(RoomStartedEvent) 커밋 후 방 전체 그룹채팅(GROUP)을 자동 생성한다.
 * 방 활성화 트랜잭션이 채팅방 생성 실패로 롤백되지 않도록 AFTER_COMMIT에서 처리한다.
 * 멤버 스냅샷은 서비스의 방 잠금 안에서 조회한다. 이후 입장은 입장 트랜잭션에서 함께 등록한다.
 */
@Component
public class GroupChatAutoCreateListener {

    private final ChatService chatService;

    public GroupChatAutoCreateListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoomStarted(RoomStartedEvent event) {
        chatService.createGroupRoom(event.roomId());
    }

    @EventListener
    public void handleMemberJoined(RoomMemberJoinedEvent event) {
        chatService.addGroupRoomMember(event.roomId(), event.userId(), event.joinedAt());
    }
}
