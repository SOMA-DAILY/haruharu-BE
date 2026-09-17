package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse.TimerType;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.son.haruharu.domain.video.implement.VideoPostReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 오늘 화면 조회. 현재 노출 중인 슬롯 창(slot_start 상대, SlotClock)과 다음 전환 시각을 파생한다. */
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

        LocalDateTime now = LocalDateTime.now(clock);
        // currentDay는 방 진행 일차 표시용(room 레벨).
        int currentDay = room.currentDayAt(now);

        // 노출 중인 슬롯 창(최근 3시간). 방 종료 이후 시작 슬롯은 업로드 불가라 제외. 촬영 가능한 '현재 슬롯'은 슬롯이 흐르는 동안만.
        List<LocalDateTime> window = room.startsAt() == null
                ? List.of()
                : SlotClock.visibleSlotStarts(room.startsAt(), now, room.videoUploadableUntil());
        LocalDateTime currentSlotStart = room.isVideoSlotOpenAt(now)
                ? SlotClock.currentSlotStart(room.startsAt(), now)
                : null;
        List<Long> roomMemberIds = roomMemberReader.getByRoomId(roomId).stream()
                .map(RoomMember::id)
                .toList();
        Set<LocalDateTime> visibleSlotStarts = window.isEmpty()
                ? Set.of()
                : Set.copyOf(videoPostReader.getVisibleSlotStarts(roomMemberIds, window));
        List<LocalDateTime> responseWindow = window.stream()
                .filter(slotStart -> slotStart.equals(currentSlotStart) || visibleSlotStarts.contains(slotStart))
                .toList();
        Set<LocalDateTime> uploadedSlots = responseWindow.isEmpty()
                ? Set.of()
                : Set.copyOf(videoPostReader.getUploadedSlotStarts(member.id(), responseWindow));

        TimerTarget timer = computeTimer(room, now);

        return TodayResponse.of(room, now, currentDay, responseWindow, currentSlotStart, uploadedSlots,
                timer.at(), timer.type());
    }

    /** 헤더 타이머가 카운트다운할 다음 전환 시각과 라벨 종류. 대상이 없으면 (null, null). */
    private record TimerTarget(LocalDateTime at, TimerType type) {
        private static final TimerTarget NONE = new TimerTarget(null, null);
    }

    /**
     * 다음 전환 시각 계산(절대시각). 새 모델(starts_at 상대·어드민 시각) 규칙 — 영상 슬롯은 방 종료까지 흐른다(#436):
     * - ACTIVE/SELECTING: 다음 슬롯 시작(NEXT_SLOT). 단 아직 오지 않은 최종선택 마일스톤(ACTIVE=최종선택 오픈,
     *   SELECTING=결과 발표, 없으면 최종선택 마감)이 다음 슬롯 이전이면 그 시각(DEADLINE)
     * - 다음 슬롯이 방 종료 이후면 대상 없음
     * - PENDING/ENDED/CANCELLED·구 모델(starts_at null): 대상 없음
     */
    private TimerTarget computeTimer(Room room, LocalDateTime now) {
        if (!room.isVideoSlotOpenAt(now)) {
            return TimerTarget.NONE;
        }
        LocalDateTime nextSlot = SlotClock.nextSlotStart(room.startsAt(), now);
        LocalDateTime milestone = room.statusAt(now) == RoomStatus.ACTIVE
                ? room.finalSelectionOpensAt()
                : (room.resultAt() != null ? room.resultAt() : room.finalSelectionClosesAt());
        if (milestone != null && now.isBefore(milestone) && !nextSlot.isBefore(milestone)) {
            return new TimerTarget(milestone, TimerType.DEADLINE);
        }
        if (room.videoUploadableUntil() != null && !nextSlot.isBefore(room.videoUploadableUntil())) {
            return TimerTarget.NONE;
        }
        return new TimerTarget(nextSlot, TimerType.NEXT_SLOT);
    }
}
