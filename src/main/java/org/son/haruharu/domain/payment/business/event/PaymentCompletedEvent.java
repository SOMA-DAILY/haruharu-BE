package org.son.haruharu.domain.payment.business.event;

/** IAP 신규 구매로 코인 충전이 확정된 유저. 커밋 후 결제 완료 통지 푸시의 트리거. */
public record PaymentCompletedEvent(
        Long userId
) {
}
