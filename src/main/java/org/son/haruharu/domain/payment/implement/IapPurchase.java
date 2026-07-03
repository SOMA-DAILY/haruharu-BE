package org.son.haruharu.domain.payment.implement;

import java.time.LocalDateTime;
import java.util.UUID;

/** 검증된 인앱결제 구매. RevenueCat webhook으로 확인된 거래만 기록된다. */
public record IapPurchase(
        UUID id,
        Long userId,
        UUID productId,
        String storeProductId,
        String storeTransactionId,
        IapStore store,
        long coinAmount,
        IapPurchaseStatus status,
        LocalDateTime purchasedAt,
        LocalDateTime refundedAt
) {
    /** 신규 구매: OWNED 상태로 생성. id는 영속화 시점에 부여, refundedAt은 null. */
    public static IapPurchase owned(
            Long userId,
            UUID productId,
            String storeProductId,
            String storeTransactionId,
            IapStore store,
            long coinAmount,
            LocalDateTime purchasedAt
    ) {
        return new IapPurchase(null, userId, productId, storeProductId, storeTransactionId,
                store, coinAmount, IapPurchaseStatus.OWNED, purchasedAt, null);
    }

    /** 환불 전이: REFUNDED로 바꾸고 환불 시각을 기록한 새 인스턴스 반환. */
    public IapPurchase refunded(LocalDateTime refundedAt) {
        return new IapPurchase(id, userId, productId, storeProductId, storeTransactionId,
                store, coinAmount, IapPurchaseStatus.REFUNDED, purchasedAt, refundedAt);
    }

    public boolean isOwned() {
        return status == IapPurchaseStatus.OWNED;
    }
}
