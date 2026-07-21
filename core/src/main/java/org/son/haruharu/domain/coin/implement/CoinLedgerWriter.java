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

    /** 데모 리셋 전용 하드삭제(#267). 해당 유저의 원장을 물리 삭제한다. */
    public void deleteByUserId(Long userId) {
        coinLedgerRepository.deleteByUserId(userId);
    }
}
