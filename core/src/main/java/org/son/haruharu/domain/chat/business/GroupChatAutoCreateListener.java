package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 소개팅방 시작(RoomStartedEvent) 커밋 후 방 전체 그룹채팅(GROUP)을 자동 생성한다.
 * 방 활성화 트랜잭션이 채팅방 생성 실패로 롤백되지 않도록 AFTER_COMMIT에서 처리한다.
 * 멤버는 이 시점의 room_members 스냅샷이며, room_id 당 1방 멱등이라 재발행에도 안전하다.
 * DIRECT 자동 생성(ChatRoomAutoCreateListener)과 같은 AFTER_COMMIT 훅이되, 이벤트 payload에 멤버가 없어
 * RoomMemberReader 로 스냅샷을 조회하는 점이 다르다(notification 의 RoomStartedEventListener 와 동일 방식).
 */
@Component
public class GroupChatAutoCreateListener {

    private final RoomMemberReader roomMemberReader;
    private final ChatService chatService;

    public GroupChatAutoCreateListener(RoomMemberReader roomMemberReader, ChatService chatService) {
        this.roomMemberReader = roomMemberReader;
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRoomStarted(RoomStartedEvent event) {
        List<Long> memberUserIds = roomMemberReader.getByRoomId(event.roomId()).stream()
                .map(RoomMember::userId)
                .toList();
        chatService.createGroupRoom(event.roomId(), memberUserIds);
    }
}
