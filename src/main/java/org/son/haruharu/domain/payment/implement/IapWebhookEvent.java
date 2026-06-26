package org.son.haruharu.domain.payment.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** RevenueCat webhook 수신 로그. eventId로 멱등 처리의 기준이 된다. */
public record IapWebhookEvent(
        UUID id,
        String eventId,
        String eventType,
        UUID purchaseId,
        String payload,
        LocalDateTime receivedAt
) {
    public static IapWebhookEvent of(
            String eventId,
            String eventType,
            UUID purchaseId,
            String payload,
            LocalDateTime receivedAt
    ) {
        return new IapWebhookEvent(null, eventId, eventType, purchaseId, payload, receivedAt);
    }
}
