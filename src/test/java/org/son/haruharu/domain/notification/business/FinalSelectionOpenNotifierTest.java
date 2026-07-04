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
class FinalSelectionOpenNotifierTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 7, 4);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock PushSendService pushSendService;

    FinalSelectionOpenNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new FinalSelectionOpenNotifier(roomReader, roomMemberReader, pushSendService);
    }

    private Room activeRoom() {
        return new Room(7L, RoomStatus.ACTIVE, DAY1, 3, DAY1.atTime(9, 0), null);
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 7L, userId, "MALE", null, DAY1.atTime(0, 0));
    }

    @Test
    @DisplayName("마지막 날 아침, 방 멤버 전원에게 FINAL_SELECTION_OPEN을 발송한다")
    void givenLastDay_whenNotify_thenPushesToAllMembers() {
        // Day3 09:05 (startedAt Day1 09:00 → 이틀 경과 → currentDay=3=totalDays)
        LocalDateTime now = DAY1.plusDays(2).atTime(9, 5);
        given(roomReader.getActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(100L, 1L), member(101L, 2L)));

        int notified = notifier.notifyFinalSelectionOpen(now);

        assertThat(notified).isEqualTo(1);
        then(pushSendService).should().sendToUsers(
                List.of(1L, 2L), NotificationType.FINAL_SELECTION_OPEN, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("아직 마지막 날이 아니면(Day2) 발송하지 않는다")
    void givenNotLastDay_whenNotify_thenSkips() {
        LocalDateTime now = DAY1.plusDays(1).atTime(9, 5); // currentDay=2 != totalDays 3
        given(roomReader.getActive()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyFinalSelectionOpen(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }
}
