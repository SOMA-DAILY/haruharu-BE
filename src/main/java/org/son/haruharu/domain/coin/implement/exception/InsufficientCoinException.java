package org.son.haruharu.domain.coin.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InsufficientCoinException extends BadRequestException {

    public InsufficientCoinException() {
        super("COIN_INSUFFICIENT", "코인 잔액이 부족합니다.");
    }
}
