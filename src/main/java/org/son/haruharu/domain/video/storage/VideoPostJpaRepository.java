package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VideoPostJpaRepository extends JpaRepository<VideoPostEntity, UUID> {

    long countByRoomMemberIdAndDayNumberAndDeletedAtIsNull(UUID roomMemberId, int dayNumber);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndDeletedAtIsNull(
            UUID roomMemberId, int dayNumber, int slotIndex);

    boolean existsByRoomMemberIdAndDayNumberAndSlotIndexAndMissionIdIsNotNull(
            UUID roomMemberId, int dayNumber, int slotIndex);

    List<VideoPostEntity> findByRoomMemberIdInAndDayNumberAndDeletedAtIsNullAndIsHiddenFalse(
            List<UUID> roomMemberIds, int dayNumber);
}
