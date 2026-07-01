package org.son.haruharu.domain.profile.storage;

import org.son.haruharu.domain.profile.implement.ProfileFieldUnlock;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlockRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ProfileFieldUnlockRepositoryImpl implements ProfileFieldUnlockRepository {

    private final ProfileFieldUnlockJpaRepository profileFieldUnlockJpaRepository;

    public ProfileFieldUnlockRepositoryImpl(ProfileFieldUnlockJpaRepository profileFieldUnlockJpaRepository) {
        this.profileFieldUnlockJpaRepository = profileFieldUnlockJpaRepository;
    }

    @Override
    public List<ProfileFieldUnlock> findByViewerAndTarget(UUID viewerRoomMemberId, UUID targetRoomMemberId) {
        return profileFieldUnlockJpaRepository
                .findByViewerRoomMemberIdAndTargetRoomMemberId(viewerRoomMemberId, targetRoomMemberId)
                .stream()
                .map(ProfileFieldUnlockEntity::toDomain)
                .toList();
    }

    @Override
    public boolean exists(UUID viewerRoomMemberId, UUID targetRoomMemberId, String fieldKey) {
        return profileFieldUnlockJpaRepository
                .existsByViewerRoomMemberIdAndTargetRoomMemberIdAndFieldKey(
                        viewerRoomMemberId, targetRoomMemberId, fieldKey);
    }

    @Override
    public ProfileFieldUnlock save(ProfileFieldUnlock unlock) {
        return profileFieldUnlockJpaRepository.save(ProfileFieldUnlockEntity.from(unlock)).toDomain();
    }
}
