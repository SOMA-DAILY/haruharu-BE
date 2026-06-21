package org.son.haruharu.domain.profile.implement;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    Optional<UserProfile> findByUserId(UUID userId);

    UserProfile save(UserProfile userProfile);
}
