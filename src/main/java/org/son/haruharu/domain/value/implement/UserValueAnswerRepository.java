package org.son.haruharu.domain.value.implement;

import java.util.List;
import java.util.Optional;

public interface UserValueAnswerRepository {

    List<UserValueAnswer> findAllByUserId(Long userId);

    Optional<UserValueAnswer> findById(Long id);

    UserValueAnswer save(UserValueAnswer userValueAnswer);
}
