package org.son.haruharu.domain.chat.business;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GroupChatMemberJoinListenerTest {

    @Mock ChatService chatService;

    GroupChatMemberJoinListener listener;

    @BeforeEach
    void setUp() {
        listener = new GroupChatMemberJoinListener(chatService);
    }

    @Test
    @DisplayName("방 멤버 입장 이벤트는 공통 GROUP 채팅 동기화로 위임한다")
    void givenRoomMemberJoinedEvent_whenHandledAfterCommit_thenDelegatesToSharedSynchronizer() {
        listener.handleRoomMemberJoined(new RoomMemberJoinedEvent(
                10L, 100L, LocalDateTime.of(2026, 7, 3, 10, 0)));

        then(chatService).should().synchronizeGroupRoom(10L);
    }

    @Test
    @DisplayName("중복 방 멤버 입장 이벤트도 멱등 서비스 경로를 두 번 호출한다")
    void givenDuplicateRoomMemberJoinedEvents_whenHandled_thenBothInvokeIdempotentSynchronizer() {
        RoomMemberJoinedEvent event = new RoomMemberJoinedEvent(
                10L, 100L, LocalDateTime.of(2026, 7, 3, 10, 0));

        listener.handleRoomMemberJoined(event);
        listener.handleRoomMemberJoined(event);

        then(chatService).should(times(2)).synchronizeGroupRoom(10L);
    }
}
