package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 매칭 완료 알림을 아침 08:00에 몰아서 발송한다. 매칭은 00:00에 확정되지만 자정 발송은 자는 유저를 깨우고
 * 방은 09:00에 열려 행동 가능 시점과도 어긋나므로, 시작 1시간 전에 배치로 보낸다.
 * 의도적으로 @Transactional 없음 — FCM IO를 트랜잭션에 묶지 않기 위함(SlotDeadlineNotifier와 동일).
 */
@Service
public class MorningMatchNotifier {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;

    public MorningMatchNotifier(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
    }

    /** today 당일 시작 예정(00:00에 확정된 PENDING) 방들에 매칭 완료 알림. 반환값은 알림 보낸 방 수. */
    public int notifyMatched(LocalDate today) {
        List<Room> rooms = roomReader.getScheduledPending(today);
        for (Room room : rooms) {
            List<Long> userIds = roomMemberReader.getByRoomId(room.id()).stream()
                    .map(RoomMember::userId)
                    .toList();
            pushSendService.sendToUsers(userIds, NotificationType.ROOM_MATCHED,
                    Map.of("roomId", room.id().toString()));
        }
        return rooms.size();
    }
}
