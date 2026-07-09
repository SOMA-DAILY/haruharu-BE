package org.son.haruharu.domain.payment.business.command;

import org.son.haruharu.domain.payment.implement.IapStore;

import java.time.LocalDateTime;

/**
 * RevenueCat webhook 이벤트를 도메인 처리에 필요한 형태로 정규화한 명령.
 * appUserId는 RevenueCat app_user_id 원문(String) — 우리 user id(Long)로 변환은 구매 처리 시점에 수행한다.
 */
public record IapWebhookCommand(
        String eventId,
        String eventType,
        String appUserId,
        String storeProductId,
        IapStore store,
        String storeTransactionId,
        LocalDateTime purchasedAt,
        String rawPayload
) {
}
