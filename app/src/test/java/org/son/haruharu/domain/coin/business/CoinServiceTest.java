package org.son.haruharu.domain.coin.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinRevokeCommand;
import org.son.haruharu.domain.coin.business.command.CoinSpendCommand;
import org.son.haruharu.domain.coin.business.dto.WalletResponse;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedger;
import org.son.haruharu.domain.coin.implement.CoinLedgerReader;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.coin.implement.CoinLedgerWriter;
import org.son.haruharu.domain.coin.implement.CoinWallet;
import org.son.haruharu.domain.coin.implement.CoinWalletReader;
import org.son.haruharu.domain.coin.implement.CoinWalletWriter;
import org.son.haruharu.domain.coin.storage.exception.CoinWalletNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoinServiceTest {

    @Mock private CoinWalletReader coinWalletReader;
    @Mock private CoinWalletWriter coinWalletWriter;
    @Mock private CoinLedgerReader coinLedgerReader;
    @Mock private CoinLedgerWriter coinLedgerWriter;

    private CoinService coinService;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        coinService = new CoinService(coinWalletReader, coinWalletWriter, coinLedgerReader, coinLedgerWriter,
                new CoinProperties(20L, true));
    }

    @Test
    @DisplayName("첫 가입 보너스 미지급 상태면 REWARD 버킷에 SIGNUP_BONUS로 설정값만큼 적립한다")
    void givenNoSignupBonus_whenGrant_thenCreditsReward() {
        given(coinLedgerReader.existsByUserIdAndReason(userId, CoinLedgerReason.SIGNUP_BONUS)).willReturn(false);
        // 지갑은 가입 시점에 생성되므로 적립 경로는 항상 존재하는 지갑을 락 조회한다(#298).
        given(coinWalletReader.getByUserIdForUpdate(userId)).willReturn(CoinWallet.init(userId));

        coinService.grantSignupBonus(userId);

        ArgumentCaptor<CoinLedger> captor = ArgumentCaptor.forClass(CoinLedger.class);
        verify(coinLedgerWriter).append(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo(CoinLedgerReason.SIGNUP_BONUS);
        assertThat(captor.getValue().bucket()).isEqualTo(CoinBucket.REWARD);
        assertThat(captor.getValue().delta()).isEqualTo(20L);
        verify(coinWalletWriter).save(any());
    }

    @Test
    @DisplayName("코인 비활성(enabled=false) 시 가입 보너스를 지급하지 않는다")
    void givenCoinDisabled_whenGrantSignupBonus_thenNoCredit() {
        CoinService disabled = new CoinService(coinWalletReader, coinWalletWriter, coinLedgerReader, coinLedgerWriter,
                new CoinProperties(20L, false));

        disabled.grantSignupBonus(userId);

        verify(coinLedgerReader, never()).existsByUserIdAndReason(any(), any());
        verify(coinLedgerWriter, never()).append(any());
        verify(coinWalletWriter, never()).save(any());
    }

    @Test
    @DisplayName("이미 첫 가입 보너스를 받은 유저면 재지급하지 않는다(멱등)")
    void givenAlreadyGranted_whenGrant_thenSkips() {
        given(coinLedgerReader.existsByUserIdAndReason(userId, CoinLedgerReason.SIGNUP_BONUS)).willReturn(true);

        coinService.grantSignupBonus(userId);

        verify(coinWalletWriter, never()).save(any());
        verify(coinLedgerWriter, never()).append(any());
    }

    @Test
    @DisplayName("지갑이 없는 사용자의 잔액 조회는 0을 반환한다")
    void givenNoWallet_whenGetWallet_thenZeroBalance() {
        // given
        given(coinWalletReader.findByUserId(userId)).willReturn(Optional.empty());

        // when
        WalletResponse response = coinService.getWallet(userId);

        // then
        assertThat(response.totalBalance()).isZero();
        assertThat(response.paidBalance()).isZero();
        assertThat(response.rewardBalance()).isZero();
    }

    @Test
    @DisplayName("적립 시 지갑 잔액 증가와 원장 1건 기록이 함께 일어난다")
    void givenWallet_whenCredit_thenSaveWalletAndAppendLedger() {
        // given
        CoinWallet wallet = new CoinWallet(1L, userId, 0, 0, null);
        given(coinWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet);

        // when
        coinService.credit(new CoinCreditCommand(
                userId, CoinBucket.PAID, 100, CoinLedgerReason.PURCHASE, "IAP_PURCHASE", "ref-1"));

        // then
        ArgumentCaptor<CoinWallet> walletCaptor = ArgumentCaptor.forClass(CoinWallet.class);
        verify(coinWalletWriter).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().paidBalance()).isEqualTo(100);

        // 원장에 거래 후 총 잔액(balance_after) 스냅샷이 기록된다
        ArgumentCaptor<CoinLedger> ledgerCaptor = ArgumentCaptor.forClass(CoinLedger.class);
        verify(coinLedgerWriter, times(1)).append(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().balanceAfter()).isEqualTo(100);
    }

    @Test
    @DisplayName("리워드+유료가 함께 차감되면 버킷별로 원장 2건이 기록된다")
    void givenRewardAndPaid_whenSpend_thenTwoLedgerEntries() {
        // given — 유료100·리워드50에서 80 소모 → 리워드50 + 유료30
        CoinWallet wallet = new CoinWallet(1L, userId, 100, 50, null);
        given(coinWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet);

        // when
        coinService.spend(new CoinSpendCommand(
                userId, 80, CoinLedgerReason.MATCH_ENTER, "MATCH", "ref-2"));

        // then
        ArgumentCaptor<CoinWallet> walletCaptor = ArgumentCaptor.forClass(CoinWallet.class);
        verify(coinWalletWriter).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().paidBalance()).isEqualTo(70);
        assertThat(walletCaptor.getValue().rewardBalance()).isZero();

        ArgumentCaptor<CoinLedger> ledgerCaptor = ArgumentCaptor.forClass(CoinLedger.class);
        verify(coinLedgerWriter, times(2)).append(ledgerCaptor.capture());
        // 총 150에서 80 소모: 리워드 -50 → 잔액 100, 유료 -30 → 잔액 70 (순차 balance_after)
        assertThat(ledgerCaptor.getAllValues())
                .anyMatch(l -> l.bucket() == CoinBucket.REWARD && l.delta() == -50 && l.balanceAfter() == 100)
                .anyMatch(l -> l.bucket() == CoinBucket.PAID && l.delta() == -30 && l.balanceAfter() == 70);
    }

    @Test
    @DisplayName("환불 회수 시 PAID를 차감하고 차감 후 총 잔액을 balance_after로 원장에 기록한다")
    void givenWallet_whenRevoke_thenAppendsLedgerWithSnapshot() {
        // paid=100, reward=20 (총 120) → 30 회수 → paid 70, 총 잔액 90
        given(coinWalletReader.getByUserIdForUpdate(userId)).willReturn(new CoinWallet(1L, userId, 100, 20, null));

        coinService.revoke(new CoinRevokeCommand(userId, 30, "REFUND", "ref-x"));

        ArgumentCaptor<CoinLedger> captor = ArgumentCaptor.forClass(CoinLedger.class);
        verify(coinLedgerWriter).append(captor.capture());
        assertThat(captor.getValue().delta()).isEqualTo(-30);
        assertThat(captor.getValue().bucket()).isEqualTo(CoinBucket.PAID);
        assertThat(captor.getValue().balanceAfter()).isEqualTo(90);
    }

    @Test
    @DisplayName("지갑이 없는 사용자가 소모를 시도하면 CoinWalletNotFoundException이 발생한다")
    void givenNoWallet_whenSpend_thenThrows() {
        // given
        given(coinWalletReader.getByUserIdForUpdate(userId)).willThrow(new CoinWalletNotFoundException());

        // when & then
        assertThatThrownBy(() -> coinService.spend(new CoinSpendCommand(
                userId, 10, CoinLedgerReason.CHAT_OPEN, null, null)))
                .isInstanceOf(CoinWalletNotFoundException.class);
    }

    @Test
    @DisplayName("회원 탈퇴 시 구름 지갑을 soft-delete 위임한다")
    void givenUserId_whenDeleteWallet_thenDelegatesSoftDelete() {
        coinService.deleteWallet(userId);

        verify(coinWalletWriter).deleteByUserId(userId);
    }

    @Test
    @DisplayName("PURCHASE 원장이 있으면 결제 이력 있음으로 판정한다(#277)")
    void givenPurchaseLedger_whenHasPurchaseHistory_thenTrue() {
        given(coinLedgerReader.existsByUserIdAndReason(userId, CoinLedgerReason.PURCHASE)).willReturn(true);

        assertThat(coinService.hasPurchaseHistory(userId)).isTrue();
    }

    @Test
    @DisplayName("PURCHASE 원장이 없으면 결제 이력 없음으로 판정한다(#277)")
    void givenNoPurchaseLedger_whenHasPurchaseHistory_thenFalse() {
        given(coinLedgerReader.existsByUserIdAndReason(userId, CoinLedgerReason.PURCHASE)).willReturn(false);

        assertThat(coinService.hasPurchaseHistory(userId)).isFalse();
    }
}
