package org.son.haruharu.domain.coin.storage;

import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CoinLedgerJpaRepository extends JpaRepository<CoinLedgerEntity, Long> {

    boolean existsByUserIdAndReason(Long userId, CoinLedgerReason reason);

    boolean existsByUserIdAndReasonAndRefId(Long userId, CoinLedgerReason reason, String refId);

    List<CoinLedgerEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<CoinLedgerEntity> findByUserIdAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Long userId, LocalDateTime before, Pageable pageable);

    // 벌크 DELETE(즉시 실행). 파생 delete는 flush 시 INSERT보다 늦게 실행돼 재지급 INSERT와 충돌하므로 사용 금지.
    // clearAutomatically는 쓰지 않는다 — 같은 리셋 트랜잭션의 미flush 변경(방 갱신 등)을 폐기하는 부작용이 있고,
    // 리셋 경로는 삭제한 원장을 이후 재조회하지 않아 stale 문제도 없다.
    @Modifying
    @Query("delete from CoinLedgerEntity c where c.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
