package org.son.haruharu.domain.video.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VideoPostJpaRepository extends JpaRepository<VideoPostEntity, UUID> {

    long countByRoomMemberIdAndUploadSlotIdInAndDeletedAtIsNull(UUID roomMemberId, List<UUID> slotIds);

    boolean existsByRoomMemberIdAndUploadSlotIdAndDeletedAtIsNull(UUID roomMemberId, UUID uploadSlotId);

    boolean existsByRoomMemberIdAndUploadSlotIdAndMissionIdIsNotNull(UUID roomMemberId, UUID uploadSlotId);

    List<VideoPostEntity> findByUploadSlotIdInAndDeletedAtIsNullAndIsHiddenFalse(List<UUID> slotIds);
}
