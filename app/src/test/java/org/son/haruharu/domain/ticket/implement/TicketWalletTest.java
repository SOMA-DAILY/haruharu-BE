package org.son.haruharu.domain.ticket.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.ticket.implement.exception.InsufficientTicketException;
import org.son.haruharu.domain.ticket.implement.exception.InvalidTicketAmountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketWalletTest {

    private static TicketWallet wallet(long balance) {
        return new TicketWallet(1L, 10L, balance, null);
    }

    @Test
    @DisplayName("신규 지갑은 잔액 0으로 시작한다")
    void givenNewUser_whenInit_thenZeroBalance() {
        assertThat(TicketWallet.init(10L).balance()).isZero();
    }

    @Test
    @DisplayName("적립하면 잔액이 그만큼 늘어난다")
    void givenWallet_whenCredit_thenBalanceIncreases() {
        assertThat(wallet(1).credit(2).balance()).isEqualTo(3);
    }

    @Test
    @DisplayName("사용하면 잔액이 그만큼 줄어든다")
    void givenEnoughBalance_whenSpend_thenBalanceDecreases() {
        assertThat(wallet(3).spend(1).balance()).isEqualTo(2);
    }

    @Test
    @DisplayName("잔액과 같은 수량은 사용할 수 있다")
    void givenExactBalance_whenSpend_thenZero() {
        assertThat(wallet(1).spend(1).balance()).isZero();
    }

    @Test
    @DisplayName("잔액을 초과해 사용하면 InsufficientTicketException이 발생한다")
    void givenInsufficientBalance_whenSpend_thenThrows() {
        assertThatThrownBy(() -> wallet(1).spend(2))
                .isInstanceOf(InsufficientTicketException.class);
    }

    @Test
    @DisplayName("환불 회수는 잔액이 충분하면 요청한 만큼 차감한다")
    void givenEnoughBalance_whenRevoke_thenDeductsAll() {
        assertThat(wallet(3).revoke(1).balance()).isEqualTo(2);
    }

    @Test
    @DisplayName("환불 회수는 잔액이 모자라도 0에서 멈춘다 — 음수가 되지 않는다")
    void givenInsufficientBalance_whenRevoke_thenStopsAtZero() {
        assertThat(wallet(1).revoke(3).balance()).isZero();
        assertThat(wallet(0).revoke(1).balance()).isZero();
    }

    @Test
    @DisplayName("0 이하 수량의 적립·사용·회수는 InvalidTicketAmountException이 발생한다")
    void givenNonPositiveAmount_whenChange_thenThrows() {
        assertThatThrownBy(() -> wallet(1).credit(0)).isInstanceOf(InvalidTicketAmountException.class);
        assertThatThrownBy(() -> wallet(1).spend(-1)).isInstanceOf(InvalidTicketAmountException.class);
        assertThatThrownBy(() -> wallet(1).revoke(0)).isInstanceOf(InvalidTicketAmountException.class);
    }
}
