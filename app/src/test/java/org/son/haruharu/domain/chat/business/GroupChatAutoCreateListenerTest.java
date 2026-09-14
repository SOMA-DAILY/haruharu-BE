package org.son.haruharu.domain.chat.business;

import java.time.LocalDateTime;

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
    @DisplayName("방 시작 이벤트 수신 시 GROUP 채팅 생성을 위임한다")
    void givenRoomStarted_whenHandle_thenCreatesGroupRoomWithAllMemberUserIds() {

        listener.handleRoomStarted(new RoomStartedEvent(10L));

        then(chatService).should().createGroupRoom(10L);
    }
    @Test
    @DisplayName("입장 이벤트 수신 시 그룹채팅 멤버 등록을 위임한다")
    void givenMemberJoined_whenHandle_thenAddsGroupMembership() {
        LocalDateTime joinedAt = LocalDateTime.now();
        listener.handleMemberJoined(new org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent(
                10L, 100L, joinedAt));
        then(chatService).should().addGroupRoomMember(10L, 100L, joinedAt);
    }
}
