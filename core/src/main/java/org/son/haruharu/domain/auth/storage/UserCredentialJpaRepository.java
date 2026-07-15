package org.son.haruharu.domain.auth.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserCredentialJpaRepository extends JpaRepository<UserCredentialEntity, Long> {

    Optional<UserCredentialEntity> findByProviderTypeAndProviderId(String providerType, String providerId);

    void deleteByUserId(Long userId);

    // 소프트삭제(탈퇴) 잔존 행을 물리 파기한다(unique(provider_type, provider_id) 재삽입 충돌 방지). native라 soft-delete 치환을 우회.
    // 반환값(파기 행 수) > 0 이면 복귀 유저 판정에 쓰인다(#245 — signup 보너스 재지급 차단).
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM user_credentials "
            + "WHERE provider_type = :providerType AND provider_id = :providerId "
            + "AND deleted_at IS NOT NULL AND deleted_at <= :threshold", nativeQuery = true)
    int purgeWithdrawnBefore(@Param("providerType") String providerType,
                             @Param("providerId") String providerId,
                             @Param("threshold") LocalDateTime threshold);
}
