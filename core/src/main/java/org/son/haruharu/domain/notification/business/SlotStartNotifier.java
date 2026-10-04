package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 방의 새 슬롯이 열리는 정각(slot_start = starts_at 상대 정시)에, 직전 2개 슬롯(now-2h, now-1h)에
 * 활성 영상을 올리지 않은 멤버에게만 SLOT_START 푸시를 보낸다(#440).
 * 초대코드(새 모델) 방만 대상 — 방마다 시각이 달라 매시 스캔하며 slot_start==now 인 방만 보낸다
 * (starts_at 은 어드민이 정시로 입력한다는 전제). FCM 발송 IO를 묶지 않도록 @Transactional 없음.
 */
@Service
public class SlotStartNotifier {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final PushSendService pushSendService;
    private final VideoPostReader videoPostReader;

    public SlotStartNotifier(RoomReader roomReader, RoomMemberReader roomMemberReader,
                             VideoPostReader videoPostReader, PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.pushSendService = pushSendService;
        this.videoPostReader = videoPostReader;
    }

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
        List<RoomMember> members = roomMemberReader.getByRoomId(room.id());
        if (members.isEmpty()) {
            return 0;
        }
        List<Long> memberIds = members.stream().map(RoomMember::id).toList();
        // 현재 시간에서 -2, -1 시간씩
        List<LocalDateTime> slotStarts = List.of(now.minusHours(2), now.minusHours(1));
        // 현재 시간에서 -2, -1시간 안에 영상을 올린 member
        List<Long> uploadedMemberIds = videoPostReader.getUploadedRoomMemberIds(memberIds, slotStarts);

        // 방의 멤버 중 uploadedMemberIds를 제외한 member
        List<Long> targetUserIds = members.stream()
                        .filter(member -> !uploadedMemberIds.contains(member.id()))
                        .map(RoomMember::userId)
                        .toList();
        if (targetUserIds.isEmpty()) {
            return 0;
        }
        // 해당 member에게 영상 발송
        pushSendService.sendToUsers(targetUserIds, NotificationType.SLOT_START,
                Map.of("roomId", room.id().toString()));
        return targetUserIds.size();
    }
}
