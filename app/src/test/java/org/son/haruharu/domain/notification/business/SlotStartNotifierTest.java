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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SlotStartNotifierTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 7, 4);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock PushSendService pushSendService;

    SlotStartNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new SlotStartNotifier(roomReader, roomMemberReader, pushSendService);
    }

    private Room activeRoom() {
        return new Room(7L, RoomStatus.ACTIVE, DAY1, 3, DAY1.atTime(9, 0), null);
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 7L, userId, "MALE", null, DAY1.atTime(0, 0));
    }

    @Test
    @DisplayName("슬롯 시작 정시(12시), 업로드 여부와 무관하게 방 전 멤버에게 SLOT_START를 발송한다")
    void givenSlotOpening_whenNotify_thenPushesToAllMembers() {
        // Day1 12:00 — DAY1_SLOT2(12~15시) 시작
        LocalDateTime now = DAY1.atTime(12, 0);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isEqualTo(2);
        then(pushSendService).should().sendToUsers(
                List.of(1L, 2L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("Day2 09시 첫 슬롯 시작 시 방 멤버에게 SLOT_START를 발송한다")
    void givenDay2FirstSlotOpening_whenNotify_thenPushes() {
        // Day2 09:00 — DAY2_SLOT1(9~12시) 시작 (startedAt Day1 09:00 → 하루 경과 → currentDay=2)
        LocalDateTime now = DAY1.plusDays(1).atTime(9, 0);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(1L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("정시에 시작하는 슬롯이 없으면 아무것도 하지 않는다")
    void givenNoOpeningSlot_whenNotify_thenSkips() {
        // 13:00 — 시작 시각이 13시인 슬롯 없음
        LocalDateTime now = DAY1.atTime(13, 0);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("세션 종료(마지막 날 경과) 방은 아직 ACTIVE여도 09시 SLOT_START 대상에서 제외된다")
    void givenSessionEndedRoom_whenNotify_thenExcludedBeforeMemberLookup() {
        // startedAt Day1 09:00, totalDays=3 → Day4(+3일) 09:00은 마지막 날 경과(hasEndedBy).
        // terminate 배치가 아직 ENDED로 전환하기 전(같은 09:00 tick)이라도 발송하면 안 된다.
        LocalDateTime now = DAY1.plusDays(3).atTime(9, 0);
        given(roomReader.getLegacyActive()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isZero();
        then(roomMemberReader).should(never()).getByRoomId(any());
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }
}
