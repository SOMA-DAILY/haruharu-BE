package org.son.haruharu.domain.identity.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserIdentityJpaRepository extends JpaRepository<UserIdentityEntity, Long> {
    Optional<UserIdentityEntity> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
    boolean existsByCiHash(String ciHash);
    void deleteByUserId(Long userId);

    // 재가입 차단 판정: 소프트삭제된(탈퇴) identity를 전역 필터 우회(native)로 조회한다.
    // deleted_at이 threshold보다 최근이면 탈퇴 후 차단 기간 내.
    @Query(value = "SELECT COUNT(*) FROM user_identities "
            + "WHERE ci_hash = :ciHash AND deleted_at IS NOT NULL AND deleted_at > :threshold", nativeQuery = true)
    long countWithdrawnByCiHashAfter(@Param("ciHash") String ciHash, @Param("threshold") LocalDateTime threshold);

    // 차단 기간이 지난 소프트삭제 잔존 행을 물리 파기한다(unique(ci_hash) 재삽입 충돌 방지). native라 soft-delete 치환을 우회.
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM user_identities "
            + "WHERE ci_hash = :ciHash AND deleted_at IS NOT NULL AND deleted_at <= :threshold", nativeQuery = true)
    int purgeWithdrawnByCiHashBefore(@Param("ciHash") String ciHash, @Param("threshold") LocalDateTime threshold);

    // 보관 기간(30일)이 지난 탈퇴 identity를 전수 물리 파기한다(개인정보 보관정책). 스케줄러가 트랜잭션 없이 호출하므로 자체 트랜잭션을 갖는다.
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM user_identities "
            + "WHERE deleted_at IS NOT NULL AND deleted_at <= :threshold", nativeQuery = true)
    int purgeWithdrawnBefore(@Param("threshold") LocalDateTime threshold);
}
