package org.son.haruharu.domain.payment.implement;


/** 티켓 상품 카탈로그. storeProductId ↔ 지급 티켓 수량 매핑의 신뢰 원천. */
public record IapProduct(
        Long id,
        String storeProductId,
        IapStore store,
        long ticketAmount,
        int price,
        String currency,
        boolean active
) {
    /** 신규 상품(활성). id는 저장 시 생성. */
    public static IapProduct create(String storeProductId, IapStore store,
                                    long ticketAmount, int price, String currency) {
        return new IapProduct(null, storeProductId, store, ticketAmount, price, currency, true);
    }
}
