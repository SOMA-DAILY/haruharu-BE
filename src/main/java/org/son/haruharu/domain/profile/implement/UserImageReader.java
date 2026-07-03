package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserImageReader {

    private final UserImageRepository userImageRepository;

    public UserImageReader(UserImageRepository userImageRepository) {
        this.userImageRepository = userImageRepository;
    }

    public List<UserImage> getAllByUserId(Long userId) {
        return userImageRepository.findAllByUserId(userId);
    }
}
