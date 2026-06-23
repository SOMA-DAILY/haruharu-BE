package org.son.haruharu.domain.profile.implement;

import org.son.haruharu.domain.profile.storage.exception.UserProfileNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserProfileReader {

    private final UserProfileRepository userProfileRepository;

    public UserProfileReader(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfile getByUserId(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(UserProfileNotFoundException::new);
    }

    public Optional<UserProfile> findByUserId(UUID userId) {
        return userProfileRepository.findByUserId(userId);
    }
}
