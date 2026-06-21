package org.son.haruharu.domain.identity.implement;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository {
    Optional<UserIdentity> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    boolean existsByCi(String ci);
    UserIdentity save(UserIdentity userIdentity);
    void deleteByUserId(UUID userId);
}
