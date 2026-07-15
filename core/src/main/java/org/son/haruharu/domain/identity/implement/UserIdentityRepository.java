package org.son.haruharu.domain.identity.implement;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserIdentityRepository {
    Optional<UserIdentity> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    boolean existsByCi(String ci);
    UserIdentity save(UserIdentity userIdentity);
    void deleteByUserId(Long userId);


    /** 보관 기간이 지난 탈퇴 identity를 전수 물리 파기한다(개인정보 보관정책). 파기 건수 반환. */
    int purgeWithdrawnBefore(LocalDateTime threshold);
}
