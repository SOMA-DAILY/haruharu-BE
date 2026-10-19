package org.son.haruharu.domain.chat.application;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.context.event.EventListener;
import org.son.haruharu.infrastructure.stomp.StompRoomClosedNotice;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupChatClosedEventListener {
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onClosedAccess(StompRoomClosedNotice notice) {
        var headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId(notice.sessionId());
        messaging.convertAndSendToUser(notice.userName(), "/queue/chat-room-events",
                new ChatLifecycleEventListener.RoomClosedPayload("CHAT_ROOM_CLOSED", notice.chatRoomId()),
                headers.getMessageHeaders());
    }
}
