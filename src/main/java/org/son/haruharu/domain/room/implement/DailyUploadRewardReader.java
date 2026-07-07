package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyUploadRewardReader {

    private final DailyUploadRewardRepository dailyUploadRewardRepository;

    public boolean isRewarded(Long roomMemberId, int dayNumber) {
        return dailyUploadRewardRepository.existsByRoomMemberIdAndDayNumber(roomMemberId, dayNumber);
    }
}
