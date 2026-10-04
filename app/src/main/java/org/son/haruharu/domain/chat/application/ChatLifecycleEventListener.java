package org.son.haruharu.domain.chat.application;

import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.domain.chat.business.event.MessageDeletedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatLifecycleEventListener {
    private final ChatDeliveryService chatDeliveryService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatLifecycleEventListener(ChatDeliveryService chatDeliveryService, SimpMessagingTemplate messagingTemplate) {
        this.chatDeliveryService = chatDeliveryService;
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(ChatMessageBroadcastEvent event) {
        chatDeliveryService.deliverMessage(event.message(), message ->
                messagingTemplate.convertAndSend("/sub/chat-rooms/" + message.chatRoomId(), message));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void close(ChatRoomClosedEvent event) {
        RoomClosedPayload payload = new RoomClosedPayload("CHAT_ROOM_CLOSED", event.chatRoomId());
        messagingTemplate.convertAndSend("/sub/chat-rooms/" + event.chatRoomId(), payload);
        event.memberUserIds().forEach(userId -> messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/chat-room-events", payload));
    }

    public record RoomClosedPayload(String type, Long chatRoomId) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void delete(MessageDeletedEvent event) {
        chatDeliveryService.deliverIfActive(event.chatRoomId(), () -> messagingTemplate.convertAndSend(
                "/sub/chat-rooms/" + event.chatRoomId() + "/events", new MessageDeletedPayload("MESSAGE_DELETED", event.messageId())));
    }

    public record MessageDeletedPayload(String type, Long messageId) {}
}
