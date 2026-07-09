package org.son.haruharu.domain.value.implement;

import org.springframework.stereotype.Component;

@Component
public class UserValueAnswerWriter {

    private final UserValueAnswerRepository userValueAnswerRepository;

    public UserValueAnswerWriter(UserValueAnswerRepository userValueAnswerRepository) {
        this.userValueAnswerRepository = userValueAnswerRepository;
    }

    public UserValueAnswer save(UserValueAnswer userValueAnswer) {
        return userValueAnswerRepository.save(userValueAnswer);
    }

    public void deleteByUserId(Long userId) {
        userValueAnswerRepository.deleteByUserId(userId);
    }
}
