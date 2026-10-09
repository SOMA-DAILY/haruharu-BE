package org.son.haruharu.domain.payment.business.event;

/** IAP 신규 구매로 티켓 충전이 확정된 유저. 커밋 후 결제 완료 통지 푸시의 트리거. ticketAmount는 통지 문구의 충전 개수. */
public record PaymentCompletedEvent(
        Long userId,
        long ticketAmount
) {
}
