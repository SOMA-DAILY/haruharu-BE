package org.son.haruharu.domain.ticket.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketLedgerRepository {

    TicketLedger save(TicketLedger ledger);

    Optional<TicketLedger> findById(Long id);

    boolean existsByUserIdAndReason(Long userId, TicketLedgerReason reason);

    /** 최신순 페이지 조회. before가 null이면 첫 페이지, 아니면 해당 시각 이전 항목을 limit개 조회. */
    List<TicketLedger> findPage(Long userId, LocalDateTime before, int limit);
}
