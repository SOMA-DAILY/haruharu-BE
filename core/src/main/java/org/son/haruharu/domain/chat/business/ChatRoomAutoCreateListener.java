package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상호매칭 성사(MutualMatchEvent) 커밋 후 두 유저의 채팅방을 자동 생성한다(#61).
 * 최종선택 트랜잭션이 채팅방 생성 실패로 롤백되지 않도록 AFTER_COMMIT에서 처리한다.
 * (match_result_id 당 1방 멱등이라 재발행에도 안전)
 */
@Component
public class ChatRoomAutoCreateListener {

    private final ChatService chatService;

    public ChatRoomAutoCreateListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMutualMatch(MutualMatchEvent event) {
        chatService.createRoomFromMatch(event.roomId(), event.matchResultId(), event.userIdA(), event.userIdB());
    }
}
