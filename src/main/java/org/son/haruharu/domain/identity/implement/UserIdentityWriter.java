package org.son.haruharu.domain.identity.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserIdentityWriter {

    private final UserIdentityRepository userIdentityRepository;

    public UserIdentity save(UserIdentity userIdentity) {
        return userIdentityRepository.save(userIdentity);
    }

    public void deleteByUserId(Long userId) {
        userIdentityRepository.deleteByUserId(userId);
    }
}
