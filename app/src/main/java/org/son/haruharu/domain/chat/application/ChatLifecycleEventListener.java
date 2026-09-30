package org.son.haruharu.domain.chat.application;

import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.infrastructure.stomp.StompDeliveryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatLifecycleEventListener {
    private final ChatDeliveryService chatDeliveryService;
    private final StompDeliveryPort deliveryPort;

    public ChatLifecycleEventListener(ChatDeliveryService chatDeliveryService, StompDeliveryPort deliveryPort) {
        this.chatDeliveryService = chatDeliveryService;
        this.deliveryPort = deliveryPort;
    }

    // 다른 인스턴스는 도착 시점에 방이 닫혔으면 버린다(guard) — 종료 알림보다 늦게 도착하는 메시지를 막는다(#453).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(ChatMessageBroadcastEvent event) {
        Long chatRoomId = event.message().chatRoomId();
        chatDeliveryService.deliverIfActive(chatRoomId, () -> deliveryPort.sendToTopic(
                "/sub/chat-rooms/" + chatRoomId, event.message(), ChatRoomDeliveryGuard.activeRoom(chatRoomId)));
    }

    // 종료 알림은 방이 닫힌 뒤에 보내는 것이라 guard 없이 전달한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void close(ChatRoomClosedEvent event) {
        RoomClosedPayload payload = new RoomClosedPayload("CHAT_ROOM_CLOSED", event.chatRoomId());
        deliveryPort.sendToTopic("/sub/chat-rooms/" + event.chatRoomId(), payload);
        event.memberUserIds().forEach(userId -> deliveryPort.sendToUser(
                userId.toString(), "/queue/chat-room-events", payload));
    }

    public record RoomClosedPayload(String type, Long chatRoomId) {}
}
