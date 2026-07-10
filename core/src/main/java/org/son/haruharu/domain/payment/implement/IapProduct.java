package org.son.haruharu.domain.payment.implement;


/** 코인 상품 카탈로그. storeProductId ↔ 지급 코인 수량 매핑의 신뢰 원천. */
public record IapProduct(
        Long id,
        String storeProductId,
        IapStore store,
        long coinAmount,
        long bonusAmount,
        int price,
        String currency,
        boolean active
) {
    /** 실제 지급 코인 = 기본 수량 + 보너스 수량. */
    public long totalCoinAmount() {
        return coinAmount + bonusAmount;
    }
}
