package org.son.haruharu.domain.coin.storage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CoinWalletJpaRepository extends JpaRepository<CoinWalletEntity, Long> {

    Optional<CoinWalletEntity> findByUserId(Long userId);

    /**
     * 잔액 변경(적립·소모·회수) 전용 조회. 행 단위 배타 락(SELECT ... FOR UPDATE)을 걸어
     * 같은 유저에 대한 동시 read-modify-write가 서로의 갱신을 덮어쓰지 못하게 한다(#298).
     * 조회 API에는 쓰지 않는다 — 읽기까지 직렬화할 이유가 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from CoinWalletEntity w where w.userId = :userId")
    Optional<CoinWalletEntity> findByUserIdForUpdate(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
