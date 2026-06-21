package org.son.haruharu.domain.value.implement;

import java.util.List;
import java.util.UUID;

public interface UserValueAnswerRepository {

    List<UserValueAnswer> findAllByUserId(UUID userId);

    UserValueAnswer save(UserValueAnswer userValueAnswer);
}
