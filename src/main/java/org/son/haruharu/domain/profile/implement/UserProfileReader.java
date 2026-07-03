package org.son.haruharu.domain.profile.implement;

import org.son.haruharu.domain.profile.storage.exception.UserProfileNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class UserProfileReader {

    private final UserProfileRepository userProfileRepository;

    public UserProfileReader(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    public UserProfile getByUserId(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseThrow(UserProfileNotFoundException::new);
    }

    public Optional<UserProfile> findByUserId(Long userId) {
        return userProfileRepository.findByUserId(userId);
    }

    /** userId 목록에 대한 프로필을 배치 조회해 userId → UserProfile 맵으로 반환한다. */
    public Map<Long, UserProfile> findByUserIdIn(Collection<Long> userIds) {
        return userProfileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserProfile::userId, Function.identity(), (a, b) -> a));
    }
}
