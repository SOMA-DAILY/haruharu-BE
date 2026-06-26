package org.son.haruharu.domain.coin.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoinLedgerRepository {

    CoinLedger save(CoinLedger ledger);

    Optional<CoinLedger> findById(UUID id);

    /** 최신순 페이지 조회. before가 null이면 첫 페이지, 아니면 해당 시각 이전 항목을 limit개 조회. */
    List<CoinLedger> findPage(UUID userId, LocalDateTime before, int limit);
}
