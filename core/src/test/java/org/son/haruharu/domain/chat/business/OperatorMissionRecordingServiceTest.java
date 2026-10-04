package org.son.haruharu.domain.chat.business;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OperatorMissionRecordingServiceTest {
    @Mock ChatRoomMemberReader chatMembers;
    @Mock ChatMessageReader messages;
    @Mock RoomReader rooms;
    @Mock RoomMemberReader members;
    @Mock VideoPostReader posts;
    LocalDateTime now = LocalDateTime.of(2026, 10, 4, 12, 0);
    OperatorMissionRecordingService service() {
        return new OperatorMissionRecordingService(chatMembers, messages, rooms, members, posts,
                Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
    }
    void setup(boolean deleted, LocalDateTime closes) {
        given(chatMembers.findMembership(1L, 7L)).willReturn(Optional.of(ChatRoomMember.join(1L, 7L, now)));
        given(messages.findById(2L)).willReturn(Optional.of(new ChatMessage(2L, 1L, 9L, "미션", now,
                new ChatMission(3L, 4L, "DAY1_1", "오늘 첫 미션"), deleted)));
        if (!deleted) given(rooms.findById(4L)).willReturn(Optional.of(Room.createScheduled(now.minusHours(1), closes,
                closes.plusHours(1), closes.plusHours(2), closes.plusDays(1), 3)));
    }
    @Test void givenNonMember_whenGetRecording_thenRejectBeforeCardLookup() {
        assertThatThrownBy(() -> service().get(1L, 2L, 7L)).isInstanceOf(ChatNotMemberException.class);
        then(messages).shouldHaveNoInteractions();
    }
    @Test void givenDeletedCard_whenGetRecording_thenUnavailable() {
        setup(true, now.plusDays(1));
        assertThat(service().get(1L, 2L, 7L).reason()).isEqualTo("DELETED");
    }
    @Test void givenFinalSelection_whenGetRecording_thenUnavailable() {
        setup(false, now);
        assertThat(service().get(1L, 2L, 7L).available()).isFalse();
        then(posts).shouldHaveNoInteractions();
    }
    @Test void givenUploadedSlot_whenGetRecording_thenReturnNextTime() {
        setup(false, now.plusDays(1));
        given(members.getByRoomIdAndUserId(4L, 7L)).willReturn(new RoomMember(8L, 4L, 7L, null, null, null, null));
        given(posts.hasActiveUpload(8L, now)).willReturn(true);
        var result = service().get(1L, 2L, 7L);
        assertThat(result.reason()).isEqualTo("ALREADY_UPLOADED");
        assertThat(result.nextAvailableAt()).isEqualTo(now.plusHours(1));
    }
}
