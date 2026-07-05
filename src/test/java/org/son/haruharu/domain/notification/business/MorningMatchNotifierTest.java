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
class MorningMatchNotifierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 4);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock PushSendService pushSendService;

    MorningMatchNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new MorningMatchNotifier(roomReader, roomMemberReader, pushSendService);
    }

    private Room pendingRoom(Long id) {
        return new Room(id, RoomStatus.PENDING, TODAY, 3, null, null);
    }

    private RoomMember member(Long memberId, Long userId, Long roomId) {
        return new RoomMember(memberId, roomId, userId, "MALE", null, TODAY.atStartOfDay());
    }

    @Test
    @DisplayName("오늘 확정된 PENDING 방마다 멤버 전원에게 ROOM_MATCHED를 roomId payload와 함께 발송한다")
    void givenTodayPendingRooms_whenNotify_thenSendsPerRoom() {
        given(roomReader.getScheduledPending(TODAY)).willReturn(List.of(pendingRoom(7L), pendingRoom(8L)));
        given(roomMemberReader.getByRoomId(7L)).willReturn(List.of(member(1L, 100L, 7L), member(2L, 101L, 7L)));
        given(roomMemberReader.getByRoomId(8L)).willReturn(List.of(member(3L, 200L, 8L)));

        int notified = notifier.notifyMatched(TODAY);

        assertThat(notified).isEqualTo(2);
        then(pushSendService).should().sendToUsers(
                List.of(100L, 101L), NotificationType.ROOM_MATCHED, Map.of("roomId", "7"));
        then(pushSendService).should().sendToUsers(
                List.of(200L), NotificationType.ROOM_MATCHED, Map.of("roomId", "8"));
    }

    @Test
    @DisplayName("오늘 확정된 방이 없으면 아무것도 발송하지 않는다")
    void givenNoRooms_whenNotify_thenSendsNothing() {
        given(roomReader.getScheduledPending(TODAY)).willReturn(List.of());

        int notified = notifier.notifyMatched(TODAY);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }
}
