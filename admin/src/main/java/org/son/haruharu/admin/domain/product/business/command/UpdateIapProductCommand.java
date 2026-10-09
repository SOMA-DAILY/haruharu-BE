package org.son.haruharu.admin.domain.product.business.command;

/** 부분 수정 — null이 아닌 필드만 갱신한다. storeProductId·store는 불변. */
public record UpdateIapProductCommand(
        Long ticketAmount,
        Integer price,
        String currency,
        Boolean active
) {
}
