package org.son.haruharu.domain.coin.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.coin.implement.exception.InsufficientCoinException;
import org.son.haruharu.domain.coin.implement.exception.InvalidCoinAmountException;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoinWalletTest {

    private CoinWallet wallet(long paid, long reward) {
        return new CoinWallet(1L, 1L, paid, reward, null);
    }

    @Test
    @DisplayName("유료100·리워드50 지갑에서 80을 소모하면 리워드50+유료30이 차감되어 유료70·리워드0이 남는다")
    void givenPaid100Reward50_whenSpend80_thenRewardFirstLeavesPaid70() {
        // given
        CoinWallet wallet = wallet(100, 50);

        // when
        CoinSpend spend = wallet.spendRewardFirst(80);

        // then
        assertThat(spend.fromReward()).isEqualTo(50);
        assertThat(spend.fromPaid()).isEqualTo(30);
        assertThat(spend.wallet().rewardBalance()).isZero();
        assertThat(spend.wallet().paidBalance()).isEqualTo(70);
    }

    @Test
    @DisplayName("리워드 잔액 이내로 소모하면 PAID는 차감되지 않는다")
    void givenEnoughReward_whenSpend_thenPaidUntouched() {
        // given
        CoinWallet wallet = wallet(100, 50);

        // when
        CoinSpend spend = wallet.spendRewardFirst(30);

        // then
        assertThat(spend.fromReward()).isEqualTo(30);
        assertThat(spend.fromPaid()).isZero();
        assertThat(spend.wallet().rewardBalance()).isEqualTo(20);
        assertThat(spend.wallet().paidBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("총잔액을 초과해 소모하면 InsufficientCoinException이 발생한다")
    void givenInsufficientTotal_whenSpend_thenThrows() {
        // given
        CoinWallet wallet = wallet(100, 50);

        // when & then
        assertThatThrownBy(() -> wallet.spendRewardFirst(151))
                .isInstanceOf(InsufficientCoinException.class);
    }

    @Test
    @DisplayName("PAID 버킷에 적립하면 paidBalance만 증가한다")
    void givenWallet_whenCreditPaid_thenOnlyPaidIncreases() {
        // given
        CoinWallet wallet = wallet(100, 50);

        // when
        CoinWallet credited = wallet.credit(CoinBucket.PAID, 200);

        // then
        assertThat(credited.paidBalance()).isEqualTo(300);
        assertThat(credited.rewardBalance()).isEqualTo(50);
    }

    @Test
    @DisplayName("환불 회수 시 PAID 잔액이 음수가 될 수 있다")
    void givenSpentPaid_whenRevoke_thenPaidCanGoNegative() {
        // given
        CoinWallet wallet = wallet(20, 0);

        // when
        CoinWallet revoked = wallet.revokePaid(100);

        // then
        assertThat(revoked.paidBalance()).isEqualTo(-80);
    }

    @Test
    @DisplayName("0 이하 금액 소모/적립은 InvalidCoinAmountException이 발생한다")
    void givenNonPositiveAmount_whenSpendOrCredit_thenThrows() {
        // given
        CoinWallet wallet = wallet(100, 50);

        // when & then
        assertThatThrownBy(() -> wallet.spendRewardFirst(0))
                .isInstanceOf(InvalidCoinAmountException.class);
        assertThatThrownBy(() -> wallet.credit(CoinBucket.REWARD, -10))
                .isInstanceOf(InvalidCoinAmountException.class);
    }
}
