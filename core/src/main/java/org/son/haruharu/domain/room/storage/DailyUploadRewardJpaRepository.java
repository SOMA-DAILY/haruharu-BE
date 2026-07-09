package org.son.haruharu.domain.room.storage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyUploadRewardJpaRepository extends JpaRepository<DailyUploadRewardEntity, Long> {

    boolean existsByRoomMemberIdAndDayNumber(Long roomMemberId, int dayNumber);
}
