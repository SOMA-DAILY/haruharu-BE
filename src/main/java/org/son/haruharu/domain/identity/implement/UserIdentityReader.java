package org.son.haruharu.domain.identity.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.identity.storage.exception.UserIdentityNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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

    /** 탈퇴한 동일 CI가 threshold 이후에 삭제됐는지 — 재가입 차단 기간 내 여부. */
    public boolean existsWithdrawnByCiWithin(String ci, LocalDateTime threshold) {
        return userIdentityRepository.existsWithdrawnByCiWithin(ci, threshold);
    }

}
