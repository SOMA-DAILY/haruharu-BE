package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoPostJpaRepository extends JpaRepository<VideoPostEntity, Long> {

    long countByRoomMemberIdAndDayNumberAndDeletedAtIsNull(Long roomMemberId, int dayNumber);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndDeletedAtIsNull(
            Long roomMemberId, int dayNumber, int slotIndex);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndMissionIdIsNotNull(
            Long roomMemberId, int dayNumber, int slotIndex);

    List<VideoPostEntity> findByRoomMemberIdInAndDayNumberAndDeletedAtIsNullAndIsHiddenFalse(
            List<Long> roomMemberIds, int dayNumber);
}
