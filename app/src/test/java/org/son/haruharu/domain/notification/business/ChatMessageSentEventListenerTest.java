package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.event.ChatMessageSentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.notification.implement.NotificationType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatMessageSentEventListenerTest {

    @Mock PushSendService pushSendService;
    @Mock BlockReader blockReader;

    ChatMessageSentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatMessageSentEventListener(pushSendService, blockReader);
    }

    @Test
    @DisplayName("채팅 메시지 이벤트 수신 시 수신자에게 CHAT 푸시를 chatRoomId payload·발신자 닉네임과 함께 발송한다")
    void givenEvent_whenHandle_thenSendsChatPushToRecipient() {
        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.DIRECT, null, 1L,
                List.of(2L), "지예", LocalDateTime.of(2026, 9, 1, 15, 0)));

        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.CHAT, Map.of("chatRoomId", "10"), "지예");
    }

    @Test
    @DisplayName("발신자를 차단한 수신자는 CHAT 푸시 대상에서 제외된다(#408)")
    void givenBlockerRecipient_whenHandle_thenExcludedFromPush() {
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of(2L));

        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "지예", LocalDateTime.of(2026, 9, 9, 15, 0)));

        then(pushSendService).should().sendToUsers(
                List.of(3L), NotificationType.CHAT, Map.of("chatRoomId", "10"), "지예");
    }

    @Test
    @DisplayName("수신자 전원이 발신자를 차단했으면 푸시를 발송하지 않는다(#408)")
    void givenAllRecipientsBlocked_whenHandle_thenNoPush() {
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of(2L, 3L));

        listener.handle(new ChatMessageSentEvent(
                20L, 10L, ChatRoomType.GROUP, 5L, 1L,
                List.of(2L, 3L), "지예", LocalDateTime.of(2026, 9, 9, 15, 0)));

        then(pushSendService).shouldHaveNoInteractions();
    }
}
