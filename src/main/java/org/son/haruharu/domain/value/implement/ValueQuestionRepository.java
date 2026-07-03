package org.son.haruharu.domain.value.implement;

import java.util.List;
import java.util.Optional;

public interface ValueQuestionRepository {

    List<ValueQuestion> findAllActive();

    Optional<ValueQuestion> findById(Long id);
}
