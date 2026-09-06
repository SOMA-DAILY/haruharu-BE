package org.son.haruharu.domain.notification.business;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.implement.VideoPostReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SlotDeadlineNotifierTest {

    // 방 시작 20:00(정시). 슬롯 마감 30분 전 = slot_start+30분(20:30·21:30·22:30…).
    private static final LocalDateTime STARTS = LocalDateTime.of(2026, 7, 4, 20, 0);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;
    @Mock PushSendService pushSendService;

    SlotDeadlineNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new SlotDeadlineNotifier(roomReader, roomMemberReader, videoPostReader, pushSendService);
    }

    private Room activeRoom() {
        return new Room(7L, RoomStatus.ACTIVE, STARTS.toLocalDate(), 3, STARTS, null,
                STARTS, STARTS.plusDays(2), STARTS.plusDays(2).plusHours(2),
                STARTS.plusDays(2).plusHours(3), STARTS.plusDays(3));
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 7L, userId, "MALE", null, null, STARTS);
    }

    @Test
    @DisplayName("마감 30분 전(slot_start+30분), 해당 슬롯 미업로드 멤버에게만 발송한다")
    void givenSlotClosingSoon_whenNotify_thenPushesOnlyToNonUploaders() {
        LocalDateTime slotStart = STARTS.plusHours(1);   // 21:00 슬롯
        LocalDateTime now = slotStart.plusMinutes(30);   // 21:30 = 마감 30분 전
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));
        given(videoPostReader.hasActiveUpload(100L, slotStart)).willReturn(true);
        given(videoPostReader.hasActiveUpload(101L, slotStart)).willReturn(false);

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.SLOT_DEADLINE_SOON, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("모든 멤버가 업로드했으면 발송하지 않는다")
    void givenAllUploaded_whenNotify_thenNoPush() {
        LocalDateTime slotStart = STARTS.plusHours(1);
        LocalDateTime now = slotStart.plusMinutes(30);
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));
        given(videoPostReader.hasActiveUpload(100L, slotStart)).willReturn(true);

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("마감 30분 전 정각이 아니면(예: +15분) 발송하지 않는다")
    void givenNotDeadlineMinute_whenNotify_thenSkips() {
        LocalDateTime now = STARTS.plusHours(1).plusMinutes(15);
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }
}
