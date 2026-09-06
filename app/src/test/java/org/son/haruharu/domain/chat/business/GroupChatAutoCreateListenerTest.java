package org.son.haruharu.domain.chat.business;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.event.RoomStartedEvent;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GroupChatAutoCreateListenerTest {

    @Mock RoomMemberReader roomMemberReader;
    @Mock ChatService chatService;

    GroupChatAutoCreateListener listener;

    @BeforeEach
    void setUp() {
        listener = new GroupChatAutoCreateListener(roomMemberReader, chatService);
    }

    private RoomMember member(Long id, Long userId) {
        return new RoomMember(id, 10L, userId, "MALE", null, null, LocalDateTime.now());
    }

    @Test
    @DisplayName("방 시작 이벤트 수신 시 방 멤버 userId 전원을 모아 GROUP 채팅 생성을 위임한다")
    void givenRoomStarted_whenHandle_thenCreatesGroupRoomWithAllMemberUserIds() {
        given(roomMemberReader.getByRoomId(10L)).willReturn(List.of(
                member(1L, 100L), member(2L, 200L), member(3L, 300L)));

        listener.handleRoomStarted(new RoomStartedEvent(10L));

        then(chatService).should().createGroupRoom(10L, List.of(100L, 200L, 300L));
    }
}
