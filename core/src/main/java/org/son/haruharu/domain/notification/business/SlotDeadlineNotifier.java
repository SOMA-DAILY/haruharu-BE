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
 * 슬롯 마감 30분 전(slot_start+30분), ACTIVE 방의 해당 슬롯 미업로드 멤버에게만 마감 임박 푸시를 보낸다.
 * 초대코드(새 모델) 방만 대상 — 방마다 시각이 달라 매시 :30 스캔하며 slot_start+30분==now 인 방만 보낸다.
 * 의도적으로 @Transactional 없음 — 트랜잭션을 열면 FCM 발송 IO가 묶이고, 발송 중 무효 토큰
 * 삭제(REQUIRED 전파)가 readOnly 트랜잭션에 조인되어 실패한다. 읽기 간 정합성도 불필요(베스트에포트 알림).
 */
@Service
public class SlotDeadlineNotifier {

    private static final int DEADLINE_LEAD_MINUTES = 30;

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final PushSendService pushSendService;

    public SlotDeadlineNotifier(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                VideoPostReader videoPostReader, PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.pushSendService = pushSendService;
    }

    /** 반환값은 푸시 대상 멤버 수(모니터링 로그용). */
    public int notifyClosingSlots(LocalDateTime now) {
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
        LocalDateTime slotStart = SlotClock.currentSlotStart(room.startsAt(), now);
        // 현재 슬롯의 마감 30분 전 정각(slot_start+30분 == now)일 때만.
        if (!slotStart.plusMinutes(DEADLINE_LEAD_MINUTES).equals(now)) {
            return 0;
        }
        List<Long> targetUserIds = roomMemberReader.getByRoomId(room.id()).stream()
                .filter(member -> !videoPostReader.hasActiveUpload(member.id(), slotStart))
                .map(RoomMember::userId)
                .toList();
        if (targetUserIds.isEmpty()) {
            return 0;
        }
        pushSendService.sendToUsers(targetUserIds, NotificationType.SLOT_DEADLINE_SOON,
                Map.of("roomId", room.id().toString()));
        return targetUserIds.size();
    }
}
