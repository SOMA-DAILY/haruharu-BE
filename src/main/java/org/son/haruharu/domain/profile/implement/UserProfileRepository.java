package org.son.haruharu.domain.profile.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {

    Optional<UserProfile> findByUserId(Long userId);

    List<UserProfile> findByUserIdIn(Collection<Long> userIds);

    UserProfile save(UserProfile userProfile);

    void deleteByUserId(Long userId);
}
