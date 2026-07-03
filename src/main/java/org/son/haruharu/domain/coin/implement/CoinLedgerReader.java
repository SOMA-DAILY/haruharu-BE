package org.son.haruharu.domain.coin.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CoinLedgerReader {

    private final CoinLedgerRepository coinLedgerRepository;

    public Optional<CoinLedger> findById(UUID id) {
        return coinLedgerRepository.findById(id);
    }

    public List<CoinLedger> findPage(Long userId, LocalDateTime before, int limit) {
        return coinLedgerRepository.findPage(userId, before, limit);
    }
}
