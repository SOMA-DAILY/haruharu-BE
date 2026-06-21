package org.son.haruharu.domain.user.implement;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findById(UUID id);
    User save(User user);
}
