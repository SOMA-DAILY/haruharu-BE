package org.son.haruharu.domain.payment.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidWebhookPayloadException extends BadRequestException {

    public InvalidWebhookPayloadException() {
        super("IAP_WEBHOOK_INVALID_PAYLOAD", "webhook 페이로드가 유효하지 않습니다.");
    }
}
