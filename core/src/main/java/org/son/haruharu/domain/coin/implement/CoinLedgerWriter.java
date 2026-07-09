package org.son.haruharu.domain.coin.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CoinLedgerWriter {

    private final CoinLedgerRepository coinLedgerRepository;

    public CoinLedger append(CoinLedger ledger) {
        return coinLedgerRepository.save(ledger);
    }
}
