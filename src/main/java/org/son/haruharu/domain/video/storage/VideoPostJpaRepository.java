package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VideoPostJpaRepository extends JpaRepository<VideoPostEntity, Long> {

    long countByRoomMemberIdAndDayNumberAndDeletedAtIsNull(Long roomMemberId, int dayNumber);

    @Query("select distinct v.slotIndex from VideoPostEntity v "
            + "where v.roomMemberId = :roomMemberId and v.dayNumber = :dayNumber and v.deletedAt is null")
    List<Integer> findUploadedSlotIndexes(@Param("roomMemberId") Long roomMemberId,
                                          @Param("dayNumber") int dayNumber);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndDeletedAtIsNull(
            Long roomMemberId, int dayNumber, int slotIndex);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndMissionIdIsNotNull(
            Long roomMemberId, int dayNumber, int slotIndex);

    List<VideoPostEntity> findByRoomMemberIdInAndDayNumberAndDeletedAtIsNullAndIsHiddenFalse(
            List<Long> roomMemberIds, int dayNumber);
}
