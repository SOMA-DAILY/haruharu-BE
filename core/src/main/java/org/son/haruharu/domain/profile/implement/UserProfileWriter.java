package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

@Component
public class UserProfileWriter {

    private final UserProfileRepository userProfileRepository;

    public UserProfileWriter(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfile save(UserProfile userProfile) {
        return userProfileRepository.save(userProfile);
    }

    public void deleteByUserId(Long userId) {
        userProfileRepository.deleteByUserId(userId);
    }
}
