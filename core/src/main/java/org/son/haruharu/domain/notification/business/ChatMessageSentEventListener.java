package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ChatMessageSentEventListener {

    private final PushSendService pushSendService;
    private final BlockReader blockReader;
    private final ChatDeliveryService chatDeliveryService;

    public ChatMessageSentEventListener(PushSendService pushSendService, BlockReader blockReader,
                                       ChatDeliveryService chatDeliveryService) {
        this.pushSendService = pushSendService;
        this.blockReader = blockReader;
        this.chatDeliveryService = chatDeliveryService;
    }

    // 커밋 후 발송 — 메시지 저장 트랜잭션이 푸시 실패로 롤백되지 않도록
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageSentEvent event) {
        if (event.recipientUserIds().isEmpty()) {
            return;
        }
        chatDeliveryService.deliverIfActive(event.chatRoomId(), () -> {
            // 발신자를 차단한 수신자는 제외한다(#408, 수신자별 단건 조회 방지).
            Set<Long> blockers = blockReader.getBlockerUserIds(event.senderUserId());
            List<Long> recipients = event.recipientUserIds().stream()
                    .filter(id -> !blockers.contains(id))
                    .toList();
            if (!recipients.isEmpty()) {
                pushSendService.sendToUsers(recipients, NotificationType.CHAT,
                        Map.of("chatRoomId", event.chatRoomId().toString()), event.senderNickname());
            }
        });
    }
}
