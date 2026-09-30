package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.config.ChatFeatureProperties;
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
    private final ChatFeatureProperties chatFeatureProperties;

    public ChatMessageSentEventListener(PushSendService pushSendService, BlockReader blockReader,
                                       ChatDeliveryService chatDeliveryService,
                                       ChatFeatureProperties chatFeatureProperties) {
        this.pushSendService = pushSendService;
        this.blockReader = blockReader;
        this.chatDeliveryService = chatDeliveryService;
        this.chatFeatureProperties = chatFeatureProperties;
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
            if (recipients.isEmpty()) {
                return;
            }
            Map<String, String> data = Map.of("chatRoomId", event.chatRoomId().toString());
            // 답장(#456): 플래그 ON 이면 원문 주인은 CHAT 대신 CHAT_REPLY 1건만 받는다(같은 사람에게 두 번 안 감).
            // 원문 주인이 수신자가 아니면(본인 답장, 차단, 운영 글 시스템 유저) 답장 푸시는 없다.
            Long replyTarget = event.replyToSenderUserId();
            boolean replyPush = chatFeatureProperties.replyPushEnabled() && replyTarget != null
                    && recipients.contains(replyTarget);
            List<Long> chatRecipients = replyPush
                    ? recipients.stream().filter(id -> !id.equals(replyTarget)).toList()
                    : recipients;
            if (replyPush) {
                pushSendService.sendToUsers(List.of(replyTarget), NotificationType.CHAT_REPLY, data,
                        event.senderNickname());
            }
            if (!chatRecipients.isEmpty()) {
                pushSendService.sendToUsers(chatRecipients, NotificationType.CHAT, data, event.senderNickname());
            }
        });
    }
}
