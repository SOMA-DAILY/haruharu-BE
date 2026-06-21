package org.son.haruharu.domain.user.implement;

import org.son.haruharu.domain.user.storage.exception.UserNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserReader {

    private final UserRepository userRepository;

    public UserReader(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(UserNotFoundException::new);
    }
}
