package org.son.haruharu.domain.coin.implement;

/**
 * 코인 출처 버킷. UX상 코인은 1종이지만, 환불 회수를 위해 서버는 출처를 분리해 관리한다.
 * - PAID: 인앱결제(IAP)로 구매한 코인. 환불 시 회수 대상.
 * - REWARD: 미션 리워드로 적립한 코인. 환불 무관.
 */
public enum CoinBucket {
    PAID,
    REWARD
}
