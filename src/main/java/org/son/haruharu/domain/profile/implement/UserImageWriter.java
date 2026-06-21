package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserImageWriter {

    private final UserImageRepository userImageRepository;

    public UserImageWriter(UserImageRepository userImageRepository) {
        this.userImageRepository = userImageRepository;
    }

    public UserImage save(UserImage userImage) {
        return userImageRepository.save(userImage);
    }

    public void deleteById(UUID id) {
        userImageRepository.deleteById(id);
    }

    public void deleteAllByUserId(UUID userId) {
        userImageRepository.deleteAllByUserId(userId);
    }
}
