package org.son.haruharu.domain.profile.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    Optional<UserProfile> findByUserId(UUID userId);

    List<UserProfile> findByUserIdIn(Collection<UUID> userIds);

    UserProfile save(UserProfile userProfile);
}
