package org.son.haruharu.domain.chat.application;

import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.ChatMessageBroadcastEvent;
import org.son.haruharu.domain.chat.business.dto.ReactionCountItem;
import org.son.haruharu.domain.chat.business.event.ChatRoomClosedEvent;
import org.son.haruharu.infrastructure.stomp.StompDeliveryPort;
import org.son.haruharu.domain.chat.business.event.MessageDeletedEvent;
import org.son.haruharu.domain.chat.business.event.ReactionUpdatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

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

    /**
     * 반응 개수 변경(#456). 기존 /sub/chat-rooms/{id} 에 섞으면 지금 앱이 메시지 payload 로 파싱하다 실패하므로
     * 새 경로 /sub/chat-rooms/{id}/events 로만 보낸다. 누가 눌렀는지는 싣지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void reactionUpdated(ReactionUpdatedEvent event) {
        Long chatRoomId = event.chatRoomId();
        chatDeliveryService.deliverIfActive(chatRoomId, () -> deliveryPort.sendToTopic(eventsTopic(chatRoomId),
                new ReactionUpdatedPayload("REACTION_UPDATED", event.messageId(), event.counts()),
                ChatRoomDeliveryGuard.activeRoom(chatRoomId)));
    }

    /** 운영 글 삭제(#457). 앱은 해당 말풍선을 '삭제된 메시지' 로 바꾼다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void messageDeleted(MessageDeletedEvent event) {
        Long chatRoomId = event.chatRoomId();
        chatDeliveryService.deliverIfActive(chatRoomId, () -> deliveryPort.sendToTopic(eventsTopic(chatRoomId),
                new MessageDeletedPayload("MESSAGE_DELETED", event.messageId()),
                ChatRoomDeliveryGuard.activeRoom(chatRoomId)));
    }

    static String eventsTopic(Long chatRoomId) {
        return "/sub/chat-rooms/" + chatRoomId + "/events";
    }

    public record RoomClosedPayload(String type, Long chatRoomId) {}

    public record MessageDeletedPayload(String type, Long messageId) {}

    public record ReactionUpdatedPayload(String type, Long messageId, List<ReactionCountItem> counts) {}
}
