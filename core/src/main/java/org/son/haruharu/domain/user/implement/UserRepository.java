package org.son.haruharu.domain.user.implement;

import java.util.Collection;
import java.util.Set;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(Long id);

    /** 유저 단위 직렬화용 비관적 락(#321). 해당 유저 행을 SELECT ... FOR UPDATE 로 잠근다. */
    Optional<User> findByIdForUpdate(Long id);

    Set<Long> findActiveIds(Collection<Long> ids);

    User save(User user);
}
