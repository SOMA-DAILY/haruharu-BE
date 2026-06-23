package org.son.haruharu.domain.identity.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.identity.storage.exception.UserIdentityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserIdentityReader {

    private final UserIdentityRepository userIdentityRepository;

    public UserIdentity getByUserId(UUID userId) {
        return userIdentityRepository.findByUserId(userId)
                .orElseThrow(UserIdentityNotFoundException::new);
    }

    public boolean existsByUserId(UUID userId) {
        return userIdentityRepository.existsByUserId(userId);
    }

}
