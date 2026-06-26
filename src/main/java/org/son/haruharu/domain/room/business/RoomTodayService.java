package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.mission.implement.Mission;
import org.son.haruharu.domain.mission.implement.MissionReader;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.mission.implement.UploadSlotReader;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 오늘 화면 조회. 슬롯 시간창의 "현재 열림" 판정에 Clock(Asia/Seoul)을 사용한다. */
@Service
@Transactional(readOnly = true)
public class RoomTodayService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final UploadSlotReader uploadSlotReader;
    private final MissionReader missionReader;
    private final VideoPostReader videoPostReader;
    private final Clock clock;

    public RoomTodayService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                            UploadSlotReader uploadSlotReader, MissionReader missionReader,
                            VideoPostReader videoPostReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.uploadSlotReader = uploadSlotReader;
        this.missionReader = missionReader;
        this.videoPostReader = videoPostReader;
        this.clock = clock;
    }

    public TodayResponse getToday(UUID roomId, UUID userId) {
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        List<UploadSlot> slots = uploadSlotReader.getByDay(room.currentDay());
        List<UUID> slotIds = slots.stream().map(UploadSlot::id).toList();
        long uploadedCount = videoPostReader.countUploaded(member.id(), slotIds);
        List<Mission> missions = missionReader.getActiveCandidates();
        int nowHour = LocalTime.now(clock).getHour();

        return TodayResponse.of(room, slots, nowHour, uploadedCount, missions);
    }
}
