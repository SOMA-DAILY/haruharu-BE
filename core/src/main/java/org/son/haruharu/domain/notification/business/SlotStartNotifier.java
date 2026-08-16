package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 방의 새 슬롯이 열리는 정각(slot_start = starts_at 상대 정시)에 ACTIVE 방의 전 멤버에게 SLOT_START 푸시.
 * 업로드 여부와 무관하게 발송한다(서로의 하루 확인 + 다음 슬롯 남기기 유도).
 * 초대코드(새 모델) 방만 대상 — 방마다 시각이 달라 매시 스캔하며 slot_start==now 인 방만 보낸다
 * (starts_at 은 어드민이 정시로 입력한다는 전제). SlotDeadlineNotifier와 동일하게 @Transactional 없음.
 */
@Service
public class SlotStartNotifier {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;

    public SlotStartNotifier(RoomReader roomReader, RoomMemberReader roomMemberReader,
                             PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
    }

    /** 반환값은 푸시 대상 멤버 수(모니터링 로그용). */
    public int notifyOpeningSlots(LocalDateTime now) {
        int notified = 0;
        for (Room room : roomReader.getReconcilable()) {
            notified += notifyRoom(room, now);
        }
        return notified;
    }

    private int notifyRoom(Room room, LocalDateTime now) {
        if (room.startsAt() == null || room.statusAt(now) != RoomStatus.ACTIVE) {
            return 0;
        }
        // 지금이 이 방의 슬롯 시작 정각(slot_start == now)일 때만. 아니면 아직 슬롯 경계가 아님.
        if (!SlotClock.currentSlotStart(room.startsAt(), now).equals(now)) {
            return 0;
        }
        List<Long> targetUserIds = roomMemberReader.getByRoomId(room.id()).stream()
                .map(RoomMember::userId)
                .toList();
        if (targetUserIds.isEmpty()) {
            return 0;
        }
        pushSendService.sendToUsers(targetUserIds, NotificationType.SLOT_START,
                Map.of("roomId", room.id().toString()));
        return targetUserIds.size();
    }
}
