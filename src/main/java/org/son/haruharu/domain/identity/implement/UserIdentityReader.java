package org.son.haruharu.domain.identity.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.identity.storage.exception.UserIdentityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserIdentityReader {

    private final UserIdentityRepository userIdentityRepository;

    public UserIdentity getByUserId(Long userId) {
        return userIdentityRepository.findByUserId(userId)
                .orElseThrow(UserIdentityNotFoundException::new);
    }

    public boolean existsByUserId(Long userId) {
        return userIdentityRepository.existsByUserId(userId);
    }

    public boolean existsByCi(String ci) {
        return userIdentityRepository.existsByCi(ci);
    }

    public Optional<UserIdentity> findByUserId(Long userId) {
        return userIdentityRepository.findByUserId(userId);
    }

}
