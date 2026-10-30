package org.son.haruharu.infrastructure.stomp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

/** outbound executor에 대기하던 메시지도 실제 WebSocket 전송 직전에 다시 확인한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatOutboundChannelInterceptor implements ExecutorChannelInterceptor {
    private final ChatDeliveryService delivery;
    private final ChatSubscriptionRegistry subscriptions;

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        Long roomId = ChatSubscriptionRegistry.roomId(SimpMessageHeaderAccessor.getDestination(message.getHeaders()));
        if (roomId == null || Boolean.TRUE.equals(message.getHeaders().get(ChatSubscriptionCloser.CLOSED_NOTIFICATION))) {
            return message;
        }
        try {
            var recipient=subscriptions.recipient(SimpMessageHeaderAccessor.getSessionId(message.getHeaders()),
                    SimpMessageHeaderAccessor.getSubscriptionId(message.getHeaders()));
            if(recipient.isEmpty()) return null;
            Long userId;
            try { userId=Long.valueOf(recipient.orElseThrow().getName()); } catch(NumberFormatException e) { return null; }
            return delivery.isActiveFor(roomId,userId) ? message : null;
        } catch (RuntimeException e) {
            log.warn("채팅 outbound 상태 확인 실패: chatRoomId={}", roomId, e);
            return null;
        }
    }
}
