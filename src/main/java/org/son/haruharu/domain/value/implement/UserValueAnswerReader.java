package org.son.haruharu.domain.value.implement;

import org.son.haruharu.domain.value.storage.exception.UserValueAnswerNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserValueAnswerReader {

    private final UserValueAnswerRepository userValueAnswerRepository;

    public UserValueAnswerReader(UserValueAnswerRepository userValueAnswerRepository) {
        this.userValueAnswerRepository = userValueAnswerRepository;
    }

    public List<UserValueAnswer> getAllByUserId(Long userId) {
        return userValueAnswerRepository.findAllByUserId(userId);
    }

    public UserValueAnswer getById(Long id) {
        return userValueAnswerRepository.findById(id)
                .orElseThrow(UserValueAnswerNotFoundException::new);
    }
}
