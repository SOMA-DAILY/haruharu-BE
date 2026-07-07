package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyUploadRewardWriter {

    private final DailyUploadRewardRepository dailyUploadRewardRepository;

    public DailyUploadReward save(DailyUploadReward reward) {
        return dailyUploadRewardRepository.save(reward);
    }
}
