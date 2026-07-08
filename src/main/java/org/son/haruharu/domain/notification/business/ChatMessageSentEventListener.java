package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
public class ChatMessageSentEventListener {

    private final PushSendService pushSendService;

    public ChatMessageSentEventListener(PushSendService pushSendService) {
        this.pushSendService = pushSendService;
    }

    // 커밋 후 발송 — 메시지 저장 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageSentEvent event) {
        pushSendService.sendToUsers(List.of(event.recipientUserId()),
                NotificationType.CHAT, Map.of("chatRoomId", event.chatRoomId().toString()),
                event.senderNickname());
    }
}
