package org.son.haruharu.domain.room.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.DailyUploadReward;
import org.son.haruharu.domain.room.implement.DailyUploadRewardRepository;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DailyUploadRewardRepositoryImpl implements DailyUploadRewardRepository {

    private final DailyUploadRewardJpaRepository jpaRepository;

    @Override
    public boolean existsByRoomMemberIdAndDayNumber(Long roomMemberId, int dayNumber) {
        return jpaRepository.existsByRoomMemberIdAndDayNumber(roomMemberId, dayNumber);
    }

    @Override
    public DailyUploadReward save(DailyUploadReward reward) {
        return jpaRepository.save(DailyUploadRewardEntity.from(reward)).toDomain();
    }
}
