package org.son.haruharu.domain.coin.implement.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidCoinAmountException extends BadRequestException {

    public InvalidCoinAmountException() {
        super("COIN_INVALID_AMOUNT", "코인 금액은 0보다 커야 합니다.");
    }
}
