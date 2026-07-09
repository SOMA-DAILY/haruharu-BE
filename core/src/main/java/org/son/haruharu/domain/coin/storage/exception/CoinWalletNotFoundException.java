package org.son.haruharu.domain.coin.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class CoinWalletNotFoundException extends NotFoundException {

    public CoinWalletNotFoundException() {
        super("COIN_WALLET_NOT_FOUND", "코인 지갑을 찾을 수 없습니다.");
    }
}
