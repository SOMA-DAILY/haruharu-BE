package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 슬롯 마감 30분 전, ACTIVE 방의 해당 슬롯 미업로드 멤버에게만 마감 임박 푸시를 보낸다.
 * 의도적으로 @Transactional 없음 — 트랜잭션을 열면 FCM 발송 IO가 묶이고, 발송 중 무효 토큰
 * 삭제(REQUIRED 전파)가 readOnly 트랜잭션에 조인되어 실패한다. 읽기 간 정합성도 불필요(베스트에포트 알림).
 */
@Service
public class SlotDeadlineNotifier {

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
        for (Room room : roomReader.getActive()) {
            notified += notifyRoom(room, now);
        }
        return notified;
    }

    private int notifyRoom(Room room, LocalDateTime now) {
        int currentDay = room.currentDayAt(now, UploadSlot.dayStartHour());
        Optional<UploadSlot> closingSlot = findClosingSlot(currentDay, now.getHour());
        if (closingSlot.isEmpty()) {
            return 0;
        }
        UploadSlot slot = closingSlot.get();
        List<Long> targetUserIds = roomMemberReader.getByRoomId(room.id()).stream()
                .filter(member -> !videoPostReader.hasActiveUpload(member.id(), slot.dayNumber(), slot.slotIndex()))
                .map(RoomMember::userId)
                .toList();
        if (targetUserIds.isEmpty()) {
            return 0;
        }
        pushSendService.sendToUsers(targetUserIds, NotificationType.SLOT_DEADLINE_SOON,
                Map.of("roomId", room.id().toString()));
        return targetUserIds.size();
    }

    /** 지금 열려 있으면서 다음 정시에 닫히는 슬롯(= 스케줄러가 30분 전에 호출하는 전제). */
    private Optional<UploadSlot> findClosingSlot(int currentDay, int hour) {
        return UploadSlot.ofDay(currentDay).stream()
                .filter(slot -> slot.isOpenAt(hour) && slot.endHour() == hour + 1)
                .findFirst();
    }
}
