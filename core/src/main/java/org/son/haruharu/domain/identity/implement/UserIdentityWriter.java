package org.son.haruharu.domain.identity.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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

    /** 보관 기간이 지난 탈퇴 identity를 전수 물리 파기한다(개인정보 보관정책). 파기 건수 반환. */
    public int purgeWithdrawnBefore(LocalDateTime threshold) {
        return userIdentityRepository.purgeWithdrawnBefore(threshold);
    }
}
