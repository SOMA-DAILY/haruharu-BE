package org.son.haruharu.infrastructure.stomp;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Component
@RequiredArgsConstructor
public class ChatSubscriptionCloser {
    // STOMP native header가 아닌 서버 내부 헤더다. 클라이언트가 같은 이름을 보내도 이 값이 되지 않는다.
    static final String CLOSED_NOTIFICATION = "haruharuChatClosedNotification";
    private final ChatSubscriptionRegistry registry;
    private final SimpMessagingTemplate messaging;
    private final ApplicationEventPublisher events;

    public void close(Long chatRoomId, Object payload) {
        var removed = registry.closeRoom(chatRoomId, () -> {
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setHeader(CLOSED_NOTIFICATION, true);
            messaging.convertAndSend("/sub/chat-rooms/" + chatRoomId, payload, headers.getMessageHeaders());
            messaging.convertAndSend("/sub/chat-rooms/" + chatRoomId + "/events", payload, headers.getMessageHeaders());
        });
        removed.forEach(s -> events.publishEvent(new SessionUnsubscribeEvent(this, s.unsubscribeMessage(), s.user())));
    }
}
