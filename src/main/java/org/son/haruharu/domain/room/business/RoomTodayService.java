package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse.RewardStatus;
import org.son.haruharu.domain.room.business.dto.TodayResponse.TimerType;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.DailyUploadRewardReader;
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
    private final DailyUploadRewardReader dailyUploadRewardReader;
    private final UploadRewardProperties uploadRewardProperties;
    private final Clock clock;

    public RoomTodayService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                            VideoPostReader videoPostReader, DailyUploadRewardReader dailyUploadRewardReader,
                            UploadRewardProperties uploadRewardProperties, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.videoPostReader = videoPostReader;
        this.dailyUploadRewardReader = dailyUploadRewardReader;
        this.uploadRewardProperties = uploadRewardProperties;
        this.clock = clock;
    }

    public TodayResponse getToday(Long roomId, Long userId) {
        RoomMember member = roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);

        // 단일 now 기준으로 currentDay·슬롯 열림·타이머를 파생해 초/일 경계에서의 내부 불일치를 방지한다.
        LocalDateTime now = LocalDateTime.now(clock);
        int currentDay = room.currentDayAt(now, UploadSlot.dayStartHour());
        List<UploadSlot> slots = UploadSlot.ofDay(currentDay);
        long uploadedCount = videoPostReader.countUploaded(member.id(), currentDay);
        int nowHour = now.getHour();

        long rewardAmount = uploadRewardProperties.amountFor(currentDay);
        RewardStatus rewardStatus = dailyUploadRewardReader.isRewarded(member.id(), currentDay)
                ? RewardStatus.EARNED : RewardStatus.LOCKED;

        TimerTarget timer = computeTimer(room, currentDay, slots, now);

        return TodayResponse.of(room, currentDay, slots, nowHour, uploadedCount, rewardAmount, rewardStatus,
                timer.at(), timer.type());
    }

    /** 헤더 타이머가 카운트다운할 다음 전환 시각과 라벨 종류. 대상이 없으면 (null, null). */
    private record TimerTarget(LocalDateTime at, TimerType type) {
        private static final TimerTarget NONE = new TimerTarget(null, null);
    }

    /**
     * 다음 전환 시각 계산(절대시각, KST). 규칙:
     * - 오늘 남은 슬롯이 있으면 그 시작시각(NEXT_SLOT)
     * - 오늘 슬롯이 끝났고 마지막 Day가 아니면 다음 날 첫 슬롯(익일 dayStartHour, NEXT_SLOT)
     * - 마지막 Day 꼬리: 마지막 슬롯 종료(=최종선택 오픈) 전이면 그 시각(DEADLINE),
     *   최종선택 진행 중이면 결과 발표(CONFIRM_HOUR, DEADLINE), 발표 후면 대상 없음
     * PENDING(currentDay=0) 등 슬롯이 없는 상태면 대상 없음.
     */
    private TimerTarget computeTimer(Room room, int currentDay, List<UploadSlot> slots, LocalDateTime now) {
        if (slots.isEmpty()) {
            return TimerTarget.NONE;
        }
        int dayStartHour = UploadSlot.dayStartHour();
        LocalDate dayDate = room.dateOfDay(currentDay, dayStartHour);

        Optional<LocalDateTime> nextSlotToday = slots.stream()
                .map(s -> dayDate.atTime(s.startHour(), 0))
                .filter(now::isBefore)
                .min(Comparator.naturalOrder());
        if (nextSlotToday.isPresent()) {
            return new TimerTarget(nextSlotToday.get(), TimerType.NEXT_SLOT);
        }

        if (currentDay < room.totalDays()) {
            LocalDateTime nextDayFirstSlot = room.dateOfDay(currentDay + 1, dayStartHour)
                    .atTime(dayStartHour, 0);
            return new TimerTarget(nextDayFirstSlot, TimerType.NEXT_SLOT);
        }

        // 마지막 Day 꼬리: 최종선택 오픈(마지막 슬롯 종료) → 결과 발표 순으로 마감을 가리킨다.
        LocalDateTime selectionOpensAt = dayDate.atTime(slots.getLast().endHour(), 0);
        LocalDateTime resultAt = dayDate.atTime(MatchingQueue.CONFIRM_HOUR, 0);
        if (now.isBefore(selectionOpensAt)) {
            return new TimerTarget(selectionOpensAt, TimerType.DEADLINE);
        }
        if (now.isBefore(resultAt)) {
            return new TimerTarget(resultAt, TimerType.DEADLINE);
        }
        return TimerTarget.NONE;
    }
}
