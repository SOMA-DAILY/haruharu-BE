package org.son.haruharu.domain.value.storage;

import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ValueQuestionRepositoryImpl implements ValueQuestionRepository {

    private final ValueQuestionJpaRepository valueQuestionJpaRepository;

    public ValueQuestionRepositoryImpl(ValueQuestionJpaRepository valueQuestionJpaRepository) {
        this.valueQuestionJpaRepository = valueQuestionJpaRepository;
    }

    @Override
    public List<ValueQuestion> findAllActive() {
        return valueQuestionJpaRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(ValueQuestionEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<ValueQuestion> findById(Long id) {
        return valueQuestionJpaRepository.findById(id)
                .map(ValueQuestionEntity::toDomain);
    }
}
