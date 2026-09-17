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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SlotStartNotifierTest {

    // 방 시작 20:00(정시). 슬롯 경계는 20:00·21:00·22:00…(starts_at 상대 정시).
    private static final LocalDateTime STARTS = LocalDateTime.of(2026, 7, 4, 20, 0);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock PushSendService pushSendService;

    SlotStartNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new SlotStartNotifier(roomReader, roomMemberReader, pushSendService);
    }

    /** startsAt 상대 스케줄이 있는 새 모델 ACTIVE 방(최종선택 2일 뒤, 종료 3일 뒤). */
    private Room activeRoom() {
        return new Room(7L, RoomStatus.ACTIVE, STARTS.toLocalDate(), 3, STARTS, null,
                STARTS, STARTS.plusDays(2), STARTS.plusDays(2).plusHours(2),
                STARTS.plusDays(2).plusHours(3), STARTS.plusDays(3));
    }

    /** 시작 전(PENDING) 방 — startsAt 미래. */
    private Room pendingRoom() {
        LocalDateTime starts = STARTS.plusDays(1);
        return new Room(8L, RoomStatus.PENDING, starts.toLocalDate(), 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3));
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 7L, userId, "MALE", null, null, STARTS);
    }

    @Test
    @DisplayName("슬롯 시작 정각(slot_start==now)이면 업로드 여부와 무관하게 방 전 멤버에게 SLOT_START를 발송한다")
    void givenSlotBoundary_whenNotify_thenPushesToAllMembers() {
        LocalDateTime now = STARTS.plusHours(3); // 23:00 = starts_at+3h 슬롯 경계
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isEqualTo(2);
        then(pushSendService).should().sendToUsers(
                List.of(1L, 2L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("최종선택 진행 중(SELECTING)이어도 방 종료 전 슬롯 경계면 발송한다(#436)")
    void givenSelectingRoom_whenNotify_thenPushes() {
        // opensAt 22:00 → 23:00은 SELECTING, 종료는 다음날
        Room selecting = new Room(7L, RoomStatus.SELECTING, STARTS.toLocalDate(), 3, STARTS, null,
                STARTS, STARTS.plusHours(2), STARTS.plusHours(5), STARTS.plusHours(6), STARTS.plusDays(1));
        given(roomReader.getReconcilable()).willReturn(List.of(selecting));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));

        int notified = notifier.notifyOpeningSlots(STARTS.plusHours(3));

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(1L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("방 종료(ends_at) 시각의 슬롯 경계에는 발송하지 않는다")
    void givenEndedRoom_whenNotify_thenSkips() {
        Room ended = new Room(7L, RoomStatus.SELECTING, STARTS.toLocalDate(), 3, STARTS, null,
                STARTS, STARTS.plusHours(1), STARTS.plusHours(2), STARTS.plusHours(2), STARTS.plusHours(3));
        given(roomReader.getReconcilable()).willReturn(List.of(ended));

        int notified = notifier.notifyOpeningSlots(STARTS.plusHours(3));

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("첫 슬롯 시작(now==starts_at)에도 발송한다")
    void givenFirstSlot_whenNotify_thenPushes() {
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));

        int notified = notifier.notifyOpeningSlots(STARTS);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(1L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("슬롯 경계 정각이 아니면(예: :30) 아무것도 하지 않는다")
    void givenNotBoundary_whenNotify_thenSkips() {
        LocalDateTime now = STARTS.plusHours(2).plusMinutes(30);
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("아직 시작 전(PENDING) 방은 멤버 조회 전에 제외된다")
    void givenPendingRoom_whenNotify_thenExcludedBeforeMemberLookup() {
        // now는 pendingRoom의 startsAt 이전 → statusAt=PENDING
        given(roomReader.getReconcilable()).willReturn(List.of(pendingRoom()));

        int notified = notifier.notifyOpeningSlots(STARTS);

        assertThat(notified).isZero();
        then(roomMemberReader).should(never()).getByRoomId(any());
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }
}
