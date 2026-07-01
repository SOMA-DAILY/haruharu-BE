package org.son.haruharu.domain.profile.implement;

import java.util.List;
import java.util.UUID;

public interface ProfileFieldUnlockRepository {

    List<ProfileFieldUnlock> findByViewerAndTarget(UUID viewerRoomMemberId, UUID targetRoomMemberId);

    boolean exists(UUID viewerRoomMemberId, UUID targetRoomMemberId, String fieldKey);

    ProfileFieldUnlock save(ProfileFieldUnlock unlock);
}
