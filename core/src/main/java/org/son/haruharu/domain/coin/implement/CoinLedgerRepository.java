package org.son.haruharu.domain.coin.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CoinLedgerRepository {

    CoinLedger save(CoinLedger ledger);

    Optional<CoinLedger> findById(Long id);

    boolean existsByUserIdAndReason(Long userId, CoinLedgerReason reason);

    boolean existsByUserIdAndReasonAndRefId(Long userId, CoinLedgerReason reason, String refId);

    /** 최신순 페이지 조회. before가 null이면 첫 페이지, 아니면 해당 시각 이전 항목을 limit개 조회. */
    List<CoinLedger> findPage(Long userId, LocalDateTime before, int limit);

    /** 데모 리셋 전용 하드삭제(#267). 해당 유저의 원장을 물리 삭제한다. */
    /** 데모 리셋 전용 하드삭제. 삭제한 행 수를 반환한다(감사 로그용, #300). */
    int deleteByUserId(Long userId);
}
