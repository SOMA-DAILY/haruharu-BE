package org.son.haruharu.domain.payment.implement;

import java.time.LocalDateTime;

/** 검증된 인앱결제 구매. RevenueCat webhook으로 확인된 거래만 기록된다. */
public record IapPurchase(
        Long id,
        Long userId,
        Long productId,
        String storeProductId,
        String storeTransactionId,
        IapStore store,
        long ticketAmount,
        long unrevokedTicketAmount,
        IapPurchaseStatus status,
        LocalDateTime purchasedAt,
        LocalDateTime refundedAt
) {
    /** 신규 구매: OWNED 상태로 생성. id는 영속화 시점에 부여, refundedAt은 null. */
    public static IapPurchase owned(
            Long userId,
            Long productId,
            String storeProductId,
            String storeTransactionId,
            IapStore store,
            long ticketAmount,
            LocalDateTime purchasedAt
    ) {
        return new IapPurchase(null, userId, productId, storeProductId, storeTransactionId,
                store, ticketAmount, 0L, IapPurchaseStatus.OWNED, purchasedAt, null);
    }

    /**
     * 환불 전이: REFUNDED로 바꾸고 환불 시각을 기록한 새 인스턴스 반환.
     * revokedTicketAmount는 실제로 회수한 수량이다. 이미 써버려 회수하지 못한 나머지는
     * unrevokedTicketAmount로 남긴다(잔액은 0 아래로 내려가지 않는다, #492).
     */
    public IapPurchase refunded(LocalDateTime refundedAt, long revokedTicketAmount) {
        return new IapPurchase(id, userId, productId, storeProductId, storeTransactionId,
                store, ticketAmount, ticketAmount - revokedTicketAmount,
                IapPurchaseStatus.REFUNDED, purchasedAt, refundedAt);
    }

    public boolean isOwned() {
        return status == IapPurchaseStatus.OWNED;
    }
}
