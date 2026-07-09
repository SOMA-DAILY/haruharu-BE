package org.son.haruharu.domain.room.implement;

public interface DailyUploadRewardRepository {

    boolean existsByRoomMemberIdAndDayNumber(Long roomMemberId, int dayNumber);

    DailyUploadReward save(DailyUploadReward reward);
}
