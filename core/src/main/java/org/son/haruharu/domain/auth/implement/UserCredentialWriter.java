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

    /** 탈퇴 시 소셜 연동을 soft-delete한다. 남은 deleted_at 이 재가입 시 복귀 유저 판정의 근거가 된다. */
    public void deleteByUserId(Long userId) {
        userCredentialRepository.deleteByUserId(userId);
    }

    /** 탈퇴 credential 잔존 행을 물리 파기한다(unique 슬롯 해제). 파기된 행 수를 반환한다(0보다 크면 복귀 유저). */
    public int purgeWithdrawnBefore(String providerType, String providerId, java.time.LocalDateTime threshold) {
        return userCredentialRepository.purgeWithdrawnBefore(providerType, providerId, threshold);
    }
}
