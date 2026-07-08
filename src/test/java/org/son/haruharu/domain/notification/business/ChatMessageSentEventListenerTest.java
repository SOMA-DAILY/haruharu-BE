package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.notification.implement.NotificationType;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatMessageSentEventListenerTest {

    @Mock PushSendService pushSendService;

    ChatMessageSentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatMessageSentEventListener(pushSendService);
    }

    @Test
    @DisplayName("채팅 메시지 이벤트 수신 시 수신자에게 CHAT 푸시를 chatRoomId payload와 함께 발송한다")
    void givenEvent_whenHandle_thenSendsChatPushToRecipient() {
        listener.handle(new ChatMessageSentEvent(10L, 2L));

        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.CHAT, Map.of("chatRoomId", "10"));
    }
}
