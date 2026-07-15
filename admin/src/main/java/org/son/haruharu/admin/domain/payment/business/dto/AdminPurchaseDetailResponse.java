package org.son.haruharu.admin.domain.payment.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.payment.implement.IapStore;

import java.time.LocalDateTime;

/** 결제 내역 상세. 목록 필드 + 스토어 거래 식별자(RevenueCat·스토어 대조용). */
public record AdminPurchaseDetailResponse(
        @Schema(description = "구매 id", example = "1") Long id,
        @Schema(description = "구매 유저 id", example = "42") Long userId,
        @Schema(description = "구매 유저 닉네임(미설정 시 null)", nullable = true, example = "하루") String nickname,
        @Schema(description = "상품 id", example = "3") Long productId,
        @Schema(description = "스토어 상품 id", example = "groom_60") String storeProductId,
        @Schema(description = "스토어 거래 id", example = "1000000123456789") String storeTransactionId,
        @Schema(description = "스토어", example = "APP_STORE") IapStore store,
        @Schema(description = "지급 코인", example = "60") long coinAmount,
        @Schema(description = "상태", example = "OWNED") IapPurchaseStatus status,
        @Schema(description = "구매 시각") LocalDateTime purchasedAt,
        @Schema(description = "환불 시각(미환불이면 null)", nullable = true) LocalDateTime refundedAt
) {
    public static AdminPurchaseDetailResponse of(IapPurchase purchase, String nickname) {
        return new AdminPurchaseDetailResponse(
                purchase.id(), purchase.userId(), nickname,
                purchase.productId(), purchase.storeProductId(), purchase.storeTransactionId(),
                purchase.store(), purchase.coinAmount(),
                purchase.status(), purchase.purchasedAt(), purchase.refundedAt());
    }
}
