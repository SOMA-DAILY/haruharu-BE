package org.son.haruharu.common.exception;

/** 형식은 맞지만 의미상 받을 수 없는 요청(422). 예: 미래 시각의 사건, 세트에 없는 카드. */
public abstract class UnprocessableEntityException extends BusinessException {

    protected UnprocessableEntityException(String code, String message) {
        super(code, message);
    }
}
