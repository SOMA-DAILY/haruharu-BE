package org.son.haruharu.domain.auth.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    /**
     * 아직 살아 있는 토큰만 revoke 한다(#417). 판단과 쓰기를 한 문장으로 합쳐 같은 토큰의 동시 재발급 중
     * 먼저 커밋한 하나만 1 을 받고, 나머지는 행 락 대기 후 재평가에서 0 을 받는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :now, r.updatedAt = :now "
            + "WHERE r.tokenHash = :tokenHash AND r.revokedAt IS NULL")
    int revokeIfActive(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    // 유저별 마지막 토큰 발급(=마지막 로그인/갱신) 시각이 [from, to) 구간인 userId. 잠수 판단 근사.
    @Query("SELECT r.userId FROM RefreshTokenEntity r GROUP BY r.userId "
            + "HAVING MAX(r.createdAt) >= :from AND MAX(r.createdAt) < :to")
    List<Long> findUserIdsLastIssuedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
