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

    // 보관 기간(30일)이 지난 탈퇴 identity를 전수 물리 파기한다(개인정보 보관정책). 스케줄러가 트랜잭션 없이 호출하므로 자체 트랜잭션을 갖는다.
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM user_identities "
            + "WHERE deleted_at IS NOT NULL AND deleted_at <= :threshold", nativeQuery = true)
    int purgeWithdrawnBefore(@Param("threshold") LocalDateTime threshold);
}
