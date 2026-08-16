package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 세션 마지막 날 아침, ACTIVE 방 멤버 전원에게 FINAL_SELECTION_OPEN 푸시.
 * 마지막 날 판별은 room.currentDayAt == room.totalDays(하드코딩 대신 방의 총 일수 기준). @Transactional 없음.
 */
@Service
public class FinalSelectionOpenNotifier {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;

    public FinalSelectionOpenNotifier(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                      PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
    }

    /** 반환값은 알림 보낸 방 수(모니터링 로그용). */
    public int notifyFinalSelectionOpen(LocalDateTime now) {
        int notified = 0;
        // 초대코드 모델 방은 방마다 opens_at 이 달라 이 고정 cron(18:05)으로 맞출 수 없다 — getLegacyActive 가
        // 조회에서 걸러내고, 전이 시점 발송은 RoomStatusReconciler → FinalSelectionOpenedEvent 가 담당한다(#320).
        for (Room room : roomReader.getLegacyActive()) {
            if (room.currentDayAt(now) != room.totalDays()) {
                continue;
            }
            List<Long> userIds = roomMemberReader.getByRoomId(room.id()).stream()
                    .map(RoomMember::userId)
                    .toList();
            pushSendService.sendToUsers(userIds, NotificationType.FINAL_SELECTION_OPEN,
                    Map.of("roomId", room.id().toString()));
            notified++;
        }
        return notified;
    }
}