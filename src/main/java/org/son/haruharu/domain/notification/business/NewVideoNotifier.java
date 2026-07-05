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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 슬롯이 닫히는 정시, 직전 슬롯에 영상을 올린 멤버가 있으면 그 방의 "본인 외 업로더가 있는" 멤버에게
 * NEW_VIDEO_POSTED 묶음 푸시(실시간 건건이 아니라 슬롯 단위 묶음). @Transactional 없음.
 */
@Service
public class NewVideoNotifier {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final PushSendService pushSendService;

    public NewVideoNotifier(RoomReader roomReader, RoomMemberReader roomMemberReader,
                            VideoPostReader videoPostReader, PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.pushSendService = pushSendService;
    }

    /** 반환값은 푸시 대상 멤버 수(모니터링 로그용). */
    public int notifyNewVideos(LocalDateTime now) {
        int notified = 0;
        for (Room room : roomReader.getActive()) {
            notified += notifyRoom(room, now);
        }
        return notified;
    }

    private int notifyRoom(Room room, LocalDateTime now) {
        int currentDay = room.currentDayAt(now, UploadSlot.dayStartHour());
        Optional<UploadSlot> endedSlot = findEndedSlot(currentDay, now.getHour());
        if (endedSlot.isEmpty()) {
            return 0;
        }
        UploadSlot slot = endedSlot.get();
        List<RoomMember> members = roomMemberReader.getByRoomId(room.id());
        Set<Long> uploaderUserIds = members.stream()
                .filter(member -> videoPostReader.hasActiveUpload(member.id(), slot.dayNumber(), slot.slotIndex()))
                .map(RoomMember::userId)
                .collect(Collectors.toSet());
        if (uploaderUserIds.isEmpty()) {
            return 0;
        }
        List<Long> targetUserIds = members.stream()
                .map(RoomMember::userId)
                // 내가 유일한 업로더면 볼 새 영상이 없으므로 제외
                .filter(userId -> !(uploaderUserIds.size() == 1 && uploaderUserIds.contains(userId)))
                .toList();
        if (targetUserIds.isEmpty()) {
            return 0;
        }
        pushSendService.sendToUsers(targetUserIds, NotificationType.NEW_VIDEO_POSTED,
                Map.of("roomId", room.id().toString()));
        return targetUserIds.size();
    }

    /** 지금 정시에 닫힌 직전 슬롯(스케줄러가 슬롯 경계 정시에 호출하는 전제). */
    private Optional<UploadSlot> findEndedSlot(int currentDay, int hour) {
        return UploadSlot.ofDay(currentDay).stream()
                .filter(slot -> slot.endHour() == hour)
                .findFirst();
    }
}
