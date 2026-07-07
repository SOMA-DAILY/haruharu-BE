package org.son.haruharu.domain.auth.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    // 유저별 마지막 토큰 발급(=마지막 로그인/갱신) 시각이 [from, to) 구간인 userId. 잠수 판단 근사.
    @Query("SELECT r.userId FROM RefreshTokenEntity r GROUP BY r.userId "
            + "HAVING MAX(r.createdAt) >= :from AND MAX(r.createdAt) < :to")
    List<Long> findUserIdsLastIssuedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
