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

    /** 탈퇴 시 소셜 연동을 soft-delete한다. deleted_at이 재가입 쿨다운 기준 시각이 된다. */
    public void deleteByUserId(Long userId) {
        userCredentialRepository.deleteByUserId(userId);
    }

    /** 차단 기간이 지난 탈퇴 credential 잔존 행을 물리 파기한다. 파기된 행 수를 반환한다(0보다 크면 복귀 유저). */
    public int purgeWithdrawnBefore(String providerType, String providerId, java.time.LocalDateTime threshold) {
        return userCredentialRepository.purgeWithdrawnBefore(providerType, providerId, threshold);
    }
}
