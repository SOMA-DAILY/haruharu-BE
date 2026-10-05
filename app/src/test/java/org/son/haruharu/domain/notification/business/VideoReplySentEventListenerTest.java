package org.son.haruharu.domain.notification.business;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.ChatDeliveryService;
import org.son.haruharu.domain.chat.business.event.VideoReplySentEvent;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.user.business.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class VideoReplySentEventListenerTest {
    @Mock PushSendService pushSendService;
    @Mock ChatDeliveryService delivery;
    @Mock BlockReader blocks;
    @Mock UserService users;
    @Mock ChatRoomMemberReader members;
    @InjectMocks VideoReplySentEventListener listener;
    final VideoReplySentEvent event = new VideoReplySentEvent(1L, 2L, 3L, "민준");

    @BeforeEach
    void setup() {
        doAnswer(
                        call -> {
                            ((Runnable) call.getArgument(1)).run();
                            return null;
                        })
                .when(delivery)
                .deliverIfActive(eq(1L), any());
    }

    @Test
    void givenActiveOwner_whenDeliver_thenOnlyOwnerReceivesReplyNotification() {
        when(members.findMembership(1L, 2L))
                .thenReturn(Optional.of(ChatRoomMember.join(1L, 2L, LocalDateTime.now())));
        listener.handle(event);
        verify(pushSendService)
                .sendToUsers(
                        List.of(2L), NotificationType.VIDEO_REPLY, Map.of("chatRoomId", "1"), "민준");
        verifyNoMoreInteractions(pushSendService);
    }

    @Test
    void givenWithdrawnOwnerAfterCommit_whenDeliver_thenNoNotification() {
        when(users.isWithdrawn(2L)).thenReturn(true);
        listener.handle(event);
        verifyNoInteractions(pushSendService);
    }

    @Test
    void givenBlockedOwnerAfterCommit_whenDeliver_thenNoNotification() {
        when(blocks.isBlockedBetween(3L, 2L)).thenReturn(true);
        listener.handle(event);
        verifyNoInteractions(pushSendService);
    }

    @Test
    void givenDepartedOwnerAfterCommit_whenDeliver_thenNoNotification() {
        listener.handle(event);
        verifyNoInteractions(pushSendService);
    }
}
