package org.son.haruharu.domain.chat.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.application.ChatLifecycleEventListener.MessageDeletedPayload;
import org.son.haruharu.domain.chat.application.ChatLifecycleEventListener.ReactionUpdatedPayload;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.dto.ReactionCountItem;
import org.son.haruharu.domain.chat.business.event.MessageDeletedEvent;
import org.son.haruharu.domain.chat.business.event.ReactionUpdatedEvent;
import org.son.haruharu.infrastructure.stomp.StompDeliveryPort;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatLifecycleEventListenerTest {

    @Mock ChatDeliveryService chatDeliveryService;
    @Mock StompDeliveryPort deliveryPort;

    private ChatLifecycleEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChatLifecycleEventListener(chatDeliveryService, deliveryPort);
    }

    private void roomIsActive() {
        willAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).given(chatDeliveryService).deliverIfActive(anyLong(), any());
    }

    @Test
    @DisplayName("반응 개수 변경은 활성 방 guard 를 붙여 /events 로 팬아웃한다(#456·#453)")
    void givenActiveRoom_whenReactionUpdated_thenFansOutToEventsTopicWithGuard() {
        roomIsActive();
        List<ReactionCountItem> counts = List.of(new ReactionCountItem("LAUGH", 2));

        listener.reactionUpdated(new ReactionUpdatedEvent(12L, 1024L, counts));

        verify(deliveryPort).sendToTopic("/sub/chat-rooms/12/events",
                new ReactionUpdatedPayload("REACTION_UPDATED", 1024L, counts),
                ChatRoomDeliveryGuard.activeRoom(12L));
    }

    @Test
    @DisplayName("운영 글 삭제는 활성 방 guard 를 붙여 /events 로 팬아웃한다(#457·#453)")
    void givenActiveRoom_whenMessageDeleted_thenFansOutToEventsTopicWithGuard() {
        roomIsActive();

        listener.messageDeleted(new MessageDeletedEvent(12L, 1024L));

        verify(deliveryPort).sendToTopic("/sub/chat-rooms/12/events",
                new MessageDeletedPayload("MESSAGE_DELETED", 1024L),
                ChatRoomDeliveryGuard.activeRoom(12L));
    }

    @Test
    @DisplayName("닫힌 방이면 반응·삭제 이벤트를 보내지 않는다")
    void givenClosedRoom_whenChatEvent_thenNothingDelivered() {
        // deliverIfActive 가 전달을 실행하지 않는 경우(mock 기본 동작)

        listener.reactionUpdated(new ReactionUpdatedEvent(12L, 1024L, List.of()));
        listener.messageDeleted(new MessageDeletedEvent(12L, 1024L));

        verifyNoInteractions(deliveryPort);
        verify(chatDeliveryService, never()).isActive(anyLong());
    }
}
