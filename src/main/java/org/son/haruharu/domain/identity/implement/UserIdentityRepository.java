package org.son.haruharu.domain.identity.implement;

import java.util.Optional;

public interface UserIdentityRepository {
    Optional<UserIdentity> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    boolean existsByCi(String ci);
    UserIdentity save(UserIdentity userIdentity);
    void deleteByUserId(Long userId);
}
