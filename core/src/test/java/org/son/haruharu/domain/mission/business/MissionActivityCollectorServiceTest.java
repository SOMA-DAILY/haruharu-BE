package org.son.haruharu.domain.mission.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.MissionMemberStateWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MissionActivityCollectorServiceTest {

    @Mock MissionMemberStateInitializer initializer;
    @Mock MissionMemberStateWriter stateWriter;
    @Mock RoomMemberReader roomMemberReader;
    private MissionActivityCollectorService service;

    @BeforeEach
    void setUp() {
        service = new MissionActivityCollectorService(initializer, stateWriter, roomMemberReader);
    }

    @Test
    @DisplayName("저장된 영상 이벤트는 원문 없이 방 멤버의 VIDEO 활동으로 수집한다")
    void givenStoredVideoEvent_whenCollect_thenRecordsVideoSignal() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 2, 9, 10);

        service.recordVideo(1L, 20L, occurredAt);

        verify(initializer).initializeActivityTargets(1L, 20L, occurredAt);
        verify(stateWriter).recordActivity(1L, 20L, ActivationChannel.VIDEO, occurredAt);
    }

    @Test
    @DisplayName("저장된 GROUP 채팅 이벤트는 roomId와 senderId로 방 멤버를 검증해 수집한다")
    void givenStoredGroupChatEvent_whenCollect_thenRecordsGroupChatSignal() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 2, 9, 10);
        RoomMember member = new RoomMember(20L, 1L, 30L, "MALE", null, null, occurredAt.minusDays(1));
        given(roomMemberReader.getByRoomIdAndUserId(1L, 30L)).willReturn(member);

        service.recordGroupChat(1L, 30L, occurredAt);

        verify(initializer).initializeActivityTargets(1L, 20L, occurredAt);
        verify(stateWriter).recordActivity(1L, 20L, ActivationChannel.GROUP_CHAT, occurredAt);
    }
}
