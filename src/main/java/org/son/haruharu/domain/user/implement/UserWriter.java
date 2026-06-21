package org.son.haruharu.domain.user.implement;

import org.springframework.stereotype.Component;

@Component
public class UserWriter {

    private final UserRepository userRepository;

    public UserWriter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
