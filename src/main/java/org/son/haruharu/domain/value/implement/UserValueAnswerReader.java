package org.son.haruharu.domain.value.implement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class UserValueAnswerReader {

    private final UserValueAnswerRepository userValueAnswerRepository;

    public UserValueAnswerReader(UserValueAnswerRepository userValueAnswerRepository) {
        this.userValueAnswerRepository = userValueAnswerRepository;
    }

    public List<UserValueAnswer> getAllByUserId(UUID userId) {
        return userValueAnswerRepository.findAllByUserId(userId);
    }
}
