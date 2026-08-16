package org.son.haruharu.domain.notification.business;

import java.time.LocalDate;
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

    private static final LocalDate DAY1 = LocalDate.of(2026, 7, 4);

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
        return new Room(7L, RoomStatus.ACTIVE, DAY1, 3, DAY1.atTime(9, 0), null);
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 7L, userId, "MALE", null, DAY1.atTime(0, 0));
    }

    @Test
    @DisplayName("마감 30분 전, 해당 슬롯 미업로드 멤버에게만 발송한다")
    void givenSlotClosingSoon_whenNotify_thenPushesOnlyToNonUploaders() {
        // Day1 11:30 — DAY1_SLOT1(9~12시) 마감 30분 전
        LocalDateTime now = DAY1.atTime(11, 30);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));
        given(videoPostReader.hasActiveUpload(100L, 1, 1)).willReturn(true);
        given(videoPostReader.hasActiveUpload(101L, 1, 1)).willReturn(false);

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.SLOT_DEADLINE_SOON, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("모든 멤버가 업로드했으면 발송하지 않는다")
    void givenAllUploaded_whenNotify_thenNoPush() {
        LocalDateTime now = DAY1.atTime(11, 30);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));
        given(videoPostReader.hasActiveUpload(100L, 1, 1)).willReturn(true);

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("마감 임박 슬롯이 없는 시각이면 아무것도 하지 않는다")
    void givenNoClosingSlot_whenNotify_thenSkips() {
        // 10:30 — 11시에 끝나는 슬롯 없음
        LocalDateTime now = DAY1.atTime(10, 30);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("슬롯 중간 시각(22:30)이면 23시에 끝나는 슬롯이 없어 발송하지 않는다")
    void givenMidSlotTime_whenNotify_thenSkips() {
        // Day2 22:30 — 열려 있는 슬롯(21~24시)은 있지만 30분 내 마감이 아님
        LocalDateTime now = DAY1.plusDays(1).atTime(22, 30);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("Day3 17:30이면 마지막 슬롯(15~18시) 미업로드 멤버에게 발송한다")
    void givenLastSlotClosing_whenNotify_thenPushes() {
        LocalDateTime now = DAY1.plusDays(2).atTime(17, 30);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));
        given(videoPostReader.hasActiveUpload(100L, 3, 3)).willReturn(false);

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(1L), NotificationType.SLOT_DEADLINE_SOON, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("Day3 23:30은 최종선택 시간이라 마감 임박 슬롯이 없어 발송하지 않는다")
    void givenDay3AfterSlots_whenNotify_thenSkips() {
        LocalDateTime now = DAY1.plusDays(2).atTime(23, 30);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyClosingSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }
}
