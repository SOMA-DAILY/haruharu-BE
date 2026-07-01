package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ProfileFieldUnlockReader {

    private final ProfileFieldUnlockRepository profileFieldUnlockRepository;

    public ProfileFieldUnlockReader(ProfileFieldUnlockRepository profileFieldUnlockRepository) {
        this.profileFieldUnlockRepository = profileFieldUnlockRepository;
    }

    public List<String> getUnlockedFieldKeys(UUID viewerRoomMemberId, UUID targetRoomMemberId) {
        return profileFieldUnlockRepository.findByViewerAndTarget(viewerRoomMemberId, targetRoomMemberId).stream()
                .map(ProfileFieldUnlock::fieldKey)
                .toList();
    }

    public boolean isUnlocked(UUID viewerRoomMemberId, UUID targetRoomMemberId, String fieldKey) {
        return profileFieldUnlockRepository.exists(viewerRoomMemberId, targetRoomMemberId, fieldKey);
    }
}
