package org.son.haruharu.domain.value.implement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserValueAnswerRepository {

    List<UserValueAnswer> findAllByUserId(UUID userId);

    Optional<UserValueAnswer> findById(UUID id);

    UserValueAnswer save(UserValueAnswer userValueAnswer);
}
