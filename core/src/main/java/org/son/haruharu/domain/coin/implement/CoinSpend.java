package org.son.haruharu.domain.coin.implement;

/**
 * 코인 소모 결과. 차감 후 지갑과, 각 버킷에서 차감된 금액을 담는다.
 * 서비스는 이 분해 정보로 버킷별 원장(coin_ledger) 항목을 기록한다.
 */
public record CoinSpend(
        CoinWallet wallet,
        long fromReward,
        long fromPaid
) {
}
