package org.son.haruharu.domain.payment.application.dto;

import org.son.haruharu.domain.payment.business.command.IapWebhookCommand;
import org.son.haruharu.domain.payment.implement.IapStore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** RevenueCat webhook 페이로드. 전역 SNAKE_CASE 매핑으로 snake_case JSON ↔ camelCase 필드 변환된다. */
public record RevenueCatWebhookRequest(Event event) {

    public record Event(
            String id,
            String type,
            String appUserId,
            String productId,
            String store,
            String transactionId,
            String originalTransactionId,
            Long purchasedAtMs,
            String environment
    ) {
    }

    public IapWebhookCommand toCommand(String rawPayload) {
        LocalDateTime purchasedAt = (event.purchasedAtMs() == null)
                ? null
                : Instant.ofEpochMilli(event.purchasedAtMs()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        return new IapWebhookCommand(
                event.id(),
                event.type(),
                event.appUserId(),
                event.productId(),
                IapStore.from(event.store()),
                event.transactionId(),
                purchasedAt,
                event.environment(),
                rawPayload
        );
    }
}
