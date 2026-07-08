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

    // 재가입 차단 판정: 소프트삭제된(탈퇴) credential을 전역 필터 우회(native)로 조회한다.
    // deleted_at이 threshold보다 최근이면 탈퇴 후 차단 기간 내.
    @Query(value = "SELECT COUNT(*) FROM user_credentials "
            + "WHERE provider_type = :providerType AND provider_id = :providerId "
            + "AND deleted_at IS NOT NULL AND deleted_at > :threshold", nativeQuery = true)
    long countWithdrawnWithin(@Param("providerType") String providerType,
                              @Param("providerId") String providerId,
                              @Param("threshold") LocalDateTime threshold);

    // 차단 기간이 지난 소프트삭제 잔존 행을 물리 파기한다(unique(provider_type, provider_id) 재삽입 충돌 방지). native라 soft-delete 치환을 우회.
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM user_credentials "
            + "WHERE provider_type = :providerType AND provider_id = :providerId "
            + "AND deleted_at IS NOT NULL AND deleted_at <= :threshold", nativeQuery = true)
    int purgeWithdrawnBefore(@Param("providerType") String providerType,
                             @Param("providerId") String providerId,
                             @Param("threshold") LocalDateTime threshold);
}
