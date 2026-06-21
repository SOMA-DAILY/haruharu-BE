package org.son.haruharu.domain.auth.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserCredentialWriter {

    private final UserCredentialRepository userCredentialRepository;

    public UserCredential save(UserCredential userCredential) {
        return userCredentialRepository.save(userCredential);
    }
}
