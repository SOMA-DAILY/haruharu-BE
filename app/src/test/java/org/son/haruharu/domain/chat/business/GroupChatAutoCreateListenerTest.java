package org.son.haruharu.domain.chat.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GroupChatAutoCreateListenerTest {

    @Mock ChatService chatService;

    GroupChatAutoCreateListener listener;

    @BeforeEach
    void setUp() {
        listener = new GroupChatAutoCreateListener(chatService);
    }

    @Test
    @DisplayName("방 시작 이벤트는 공통 GROUP 채팅 동기화로 위임한다")
    void givenRoomStartedEvent_whenHandledAfterCommit_thenDelegatesToSharedSynchronizer() {
        listener.handleRoomStarted(new RoomStartedEvent(10L));

        then(chatService).should().synchronizeGroupRoom(10L);
    }
}
