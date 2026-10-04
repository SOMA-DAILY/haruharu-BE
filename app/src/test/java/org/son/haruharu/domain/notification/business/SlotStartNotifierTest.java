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
class SlotStartNotifierTest {

    // 방 시작 20:00(정시). 슬롯 경계는 20:00·21:00·22:00…(starts_at 상대 정시).
    private static final LocalDateTime STARTS = LocalDateTime.of(2026, 7, 4, 20, 0);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;
    @Mock PushSendService pushSendService;

    SlotStartNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new SlotStartNotifier(roomReader, roomMemberReader, videoPostReader, pushSendService);
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

    /** now 기준 직전 2개 슬롯(now-2h, now-1h). 이번 슬롯은 막 열려 업로드가 없으므로 보지 않는다. */
    private static List<LocalDateTime> recentTwoSlots(LocalDateTime now) {
        return List.of(now.minusHours(2), now.minusHours(1));
    }

    @Test
    @DisplayName("슬롯 시작 정각에 직전 2개 슬롯 모두 업로드하지 않은 멤버에게 SLOT_START를 발송한다")
    void givenNoRecentUpload_whenNotify_thenPushesToAllMembers() {
        LocalDateTime now = STARTS.plusHours(3); // 23:00 = starts_at+3h 슬롯 경계
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));
        given(videoPostReader.getUploadedRoomMemberIds(List.of(100L, 101L), recentTwoSlots(now)))
                .willReturn(List.of());

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isEqualTo(2);
        then(pushSendService).should().sendToUsers(
                List.of(1L, 2L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("직전 2개 슬롯 중 하나라도 업로드한 멤버는 발송 대상에서 제외한다")
    void givenMemberUploadedRecently_whenNotify_thenExcludesThatMember() {
        LocalDateTime now = STARTS.plusHours(3);
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));
        given(videoPostReader.getUploadedRoomMemberIds(List.of(100L, 101L), recentTwoSlots(now)))
                .willReturn(List.of(100L));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("업로드 조회 범위는 직전 2개 슬롯(now-2h, now-1h)이다 — 이번 슬롯과 3시간 전 슬롯은 보지 않는다")
    void givenSlotBoundary_whenNotify_thenQueriesOnlyPreviousTwoSlots() {
        LocalDateTime now = STARTS.plusHours(5);
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));
        given(videoPostReader.getUploadedRoomMemberIds(anyList(), anyList())).willReturn(List.of());

        notifier.notifyOpeningSlots(now);

        then(videoPostReader).should().getUploadedRoomMemberIds(
                List.of(100L), List.of(now.minusHours(2), now.minusHours(1)));
    }

    @Test
    @DisplayName("첫 슬롯 시작(now==starts_at)에는 직전 슬롯이 방 시작 전이라 전원에게 발송한다")
    void givenFirstSlot_whenNotify_thenPushesToAllMembers() {
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L)));
        given(videoPostReader.getUploadedRoomMemberIds(anyList(), anyList())).willReturn(List.of());

        int notified = notifier.notifyOpeningSlots(STARTS);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(1L), NotificationType.SLOT_START, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("멤버 전원이 직전 2개 슬롯에 업로드했으면 발송하지 않는다")
    void givenAllMembersUploadedRecently_whenNotify_thenSkipsPush() {
        LocalDateTime now = STARTS.plusHours(3);
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));
        given(videoPostReader.getUploadedRoomMemberIds(anyList(), anyList())).willReturn(List.of(100L, 101L));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("슬롯 경계 정각이 아니면(예: :30) 아무것도 하지 않는다")
    void givenNotBoundary_whenNotify_thenSkips() {
        LocalDateTime now = STARTS.plusHours(2).plusMinutes(30);
        given(roomReader.getReconcilable()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyOpeningSlots(now);

        assertThat(notified).isZero();
        then(videoPostReader).should(never()).getUploadedRoomMemberIds(anyList(), anyList());
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
