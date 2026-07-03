package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

@Component
public class UserImageWriter {

    private final UserImageRepository userImageRepository;

    public UserImageWriter(UserImageRepository userImageRepository) {
        this.userImageRepository = userImageRepository;
    }

    public UserImage save(UserImage userImage) {
        return userImageRepository.save(userImage);
    }

    public void deleteById(Long id) {
        userImageRepository.deleteById(id);
    }

    public void deleteAllByUserId(Long userId) {
        userImageRepository.deleteAllByUserId(userId);
    }
}
