package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오늘 화면 조회. 슬롯 시간창의 "현재 열림" 판정에 Clock(Asia/Seoul)을 사용한다. */
@Service
@Transactional(readOnly = true)
public class RoomTodayService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final VideoPostReader videoPostReader;
    private final Clock clock;

    public RoomTodayService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                            VideoPostReader videoPostReader, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.clock = clock;
    }

    public TodayResponse getToday(Long roomId, Long userId) {
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        int currentDay = room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour());
        List<UploadSlot> slots = UploadSlot.ofDay(currentDay);
        long uploadedCount = videoPostReader.countUploaded(member.id(), currentDay);
        int nowHour = LocalTime.now(clock).getHour();

        return TodayResponse.of(room, currentDay, slots, nowHour, uploadedCount);
    }
}
