package org.son.haruharu.domain.value.implement;

import org.springframework.stereotype.Component;

@Component
public class ValueAnswerUnlockWriter {

    private final ValueAnswerUnlockRepository valueAnswerUnlockRepository;

    public ValueAnswerUnlockWriter(ValueAnswerUnlockRepository valueAnswerUnlockRepository) {
        this.valueAnswerUnlockRepository = valueAnswerUnlockRepository;
    }

    public ValueAnswerUnlock save(ValueAnswerUnlock unlock) {
        return valueAnswerUnlockRepository.save(unlock);
    }
}