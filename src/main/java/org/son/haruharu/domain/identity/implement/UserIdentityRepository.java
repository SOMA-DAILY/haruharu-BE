package org.son.haruharu.domain.identity.implement;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserIdentityRepository {
    Optional<UserIdentity> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    boolean existsByCi(String ci);
    UserIdentity save(UserIdentity userIdentity);
    void deleteByUserId(Long userId);

    /** 탈퇴(soft-delete)된 동일 CI가 threshold 이후에 삭제됐는지 — 재가입 차단 기간 내 여부. */
    boolean existsWithdrawnByCiWithin(String ci, LocalDateTime threshold);

    /** 차단 기간이 지난 탈퇴 identity를 물리 파기한다(재삽입 유니크 충돌 방지). */
    void purgeWithdrawnByCiBefore(String ci, LocalDateTime threshold);

    /** 보관 기간이 지난 탈퇴 identity를 전수 물리 파기한다(개인정보 보관정책). 파기 건수 반환. */
    int purgeWithdrawnBefore(LocalDateTime threshold);
}
