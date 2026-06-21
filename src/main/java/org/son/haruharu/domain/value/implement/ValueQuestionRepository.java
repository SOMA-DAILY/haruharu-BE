package org.son.haruharu.domain.value.implement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ValueQuestionRepository {

    List<ValueQuestion> findAllActive();

    Optional<ValueQuestion> findById(UUID id);
}
