package org.son.haruharu.domain.value.implement;

import org.son.haruharu.domain.value.storage.exception.ValueQuestionNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ValueQuestionReader {

    private final ValueQuestionRepository valueQuestionRepository;

    public ValueQuestionReader(ValueQuestionRepository valueQuestionRepository) {
        this.valueQuestionRepository = valueQuestionRepository;
    }

    public List<ValueQuestion> getAllActive() {
        return valueQuestionRepository.findAllActive();
    }

    public ValueQuestion getById(Long id) {
        return valueQuestionRepository.findById(id)
                .orElseThrow(ValueQuestionNotFoundException::new);
    }
}
