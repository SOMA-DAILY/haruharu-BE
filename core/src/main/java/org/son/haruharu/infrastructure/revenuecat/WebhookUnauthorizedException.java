package org.son.haruharu.infrastructure.revenuecat;

import org.son.haruharu.common.exception.UnauthorizedException;

public class WebhookUnauthorizedException extends UnauthorizedException {

    public WebhookUnauthorizedException() {
        super("IAP_WEBHOOK_UNAUTHORIZED", "유효하지 않은 webhook 요청입니다.");
    }
}
