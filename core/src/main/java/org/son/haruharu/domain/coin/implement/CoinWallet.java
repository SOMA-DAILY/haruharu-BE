package org.son.haruharu.domain.coin.implement;

import org.son.haruharu.domain.coin.implement.exception.InsufficientCoinException;
import org.son.haruharu.domain.coin.implement.exception.InvalidCoinAmountException;

import java.time.LocalDateTime;

/**
 * 코인 지갑. 유료(paid)/리워드(reward) 잔액을 분리해 보관한다.
 * 모든 변경은 새 인스턴스를 반환한다(불변). updatedAt은 영속 계층(Auditing)이 관리한다.
 */
public record CoinWallet(
        Long id,
        Long userId,
        long paidBalance,
        long rewardBalance,
        LocalDateTime updatedAt
) {
    /** 신규 지갑: 잔액 0. id/updatedAt은 영속화 시점에 부여된다. */
    public static CoinWallet init(Long userId) {
        return new CoinWallet(null, userId, 0L, 0L, null);
    }

    public long totalBalance() {
        return paidBalance + rewardBalance;
    }

    /** 적립: 지정한 버킷에 코인을 더한 새 지갑을 반환한다. */
    public CoinWallet credit(CoinBucket bucket, long amount) {
        requirePositive(amount);
        return switch (bucket) {
            case PAID -> new CoinWallet(id, userId, paidBalance + amount, rewardBalance, updatedAt);
            case REWARD -> new CoinWallet(id, userId, paidBalance, rewardBalance + amount, updatedAt);
        };
    }

    /**
     * 소모(리워드 먼저): REWARD 잔액을 먼저 차감하고, 부족분만 PAID에서 차감한다.
     * 유료 잔액을 최대한 남겨 환불 회수(clawback) 적자를 최소화하기 위함이다.
     * 총잔액이 부족하면 {@link InsufficientCoinException}.
     */
    public CoinSpend spendRewardFirst(long amount) {
        requirePositive(amount);
        if (amount > totalBalance()) {
            throw new InsufficientCoinException();
        }
        long fromReward = Math.min(rewardBalance, amount);
        long fromPaid = amount - fromReward;
        CoinWallet next = new CoinWallet(id, userId, paidBalance - fromPaid, rewardBalance - fromReward, updatedAt);
        return new CoinSpend(next, fromReward, fromPaid);
    }

    /**
     * 환불 회수: PAID 잔액에서 차감한다. 이미 소진되었으면 음수가 될 수 있다(허용).
     */
    public CoinWallet revokePaid(long amount) {
        requirePositive(amount);
        return new CoinWallet(id, userId, paidBalance - amount, rewardBalance, updatedAt);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new InvalidCoinAmountException();
        }
    }
}
