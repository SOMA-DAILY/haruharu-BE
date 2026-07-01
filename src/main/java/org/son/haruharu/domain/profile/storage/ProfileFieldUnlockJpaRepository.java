package org.son.haruharu.domain.profile.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileFieldUnlockJpaRepository extends JpaRepository<ProfileFieldUnlockEntity, UUID> {

    List<ProfileFieldUnlockEntity> findByViewerRoomMemberIdAndTargetRoomMemberId(
            UUID viewerRoomMemberId, UUID targetRoomMemberId);

    boolean existsByViewerRoomMemberIdAndTargetRoomMemberIdAndFieldKey(
            UUID viewerRoomMemberId, UUID targetRoomMemberId, String fieldKey);
}
