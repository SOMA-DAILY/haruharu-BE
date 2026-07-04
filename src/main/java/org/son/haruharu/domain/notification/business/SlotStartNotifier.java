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
 * 슬롯이 새로 열리는 정시, ACTIVE 방의 해당 슬롯 미업로드 멤버에게 SLOT_START 푸시.
 * SlotDeadlineNotifier와 동일하게 @Transactional 없음(발송 IO를 트랜잭션에 묶지 않기 위함).
 */
@Service
public class SlotStartNotifier {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final PushSendService pushSendService;

    public SlotStartNotifier(RoomReader roomReader, RoomMemberReader roomMemberReader,
                             VideoPostReader videoPostReader, PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.pushSendService = pushSendService;
    }

    /** 반환값은 푸시 대상 멤버 수(모니터링 로그용). */
    public int notifyOpeningSlots(LocalDateTime now) {
        int notified = 0;
        for (Room room : roomReader.getActive()) {
            notified += notifyRoom(room, now);
        }
        return notified;
    }

    private int notifyRoom(Room room, LocalDateTime now) {
        int currentDay = room.currentDayAt(now, UploadSlot.dayStartHour());
        Optional<UploadSlot> openingSlot = findOpeningSlot(currentDay, now.getHour());
        if (openingSlot.isEmpty()) {
            return 0;
        }
        UploadSlot slot = openingSlot.get();
        List<Long> targetUserIds = roomMemberReader.getByRoomId(room.id()).stream()
                .filter(member -> !videoPostReader.hasActiveUpload(member.id(), slot.dayNumber(), slot.slotIndex()))
                .map(RoomMember::userId)
                .toList();
        if (targetUserIds.isEmpty()) {
            return 0;
        }
        pushSendService.sendToUsers(targetUserIds, NotificationType.SLOT_START,
                Map.of("roomId", room.id().toString()));
        return targetUserIds.size();
    }

    /** 지금 정시에 열리는 슬롯(스케줄러가 슬롯 시작 시각에 호출하는 전제). */
    private Optional<UploadSlot> findOpeningSlot(int currentDay, int hour) {
        return UploadSlot.ofDay(currentDay).stream()
                .filter(slot -> slot.startHour() == hour)
                .findFirst();
    }
}