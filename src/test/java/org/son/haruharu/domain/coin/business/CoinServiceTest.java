package org.son.haruharu.domain.coin.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CoinServiceTest {

    @Mock private CoinWalletReader coinWalletReader;
    @Mock private CoinWalletWriter coinWalletWriter;
    @Mock private CoinLedgerReader coinLedgerReader;
    @Mock private CoinLedgerWriter coinLedgerWriter;

    private CoinService coinService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        coinService = new CoinService(coinWalletReader, coinWalletWriter, coinLedgerReader, coinLedgerWriter);
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
        CoinWallet wallet = new CoinWallet(UUID.randomUUID(), userId, 0, 0, null);
        given(coinWalletReader.findByUserId(userId)).willReturn(Optional.of(wallet));

        // when
        coinService.credit(new CoinCreditCommand(
                userId, CoinBucket.PAID, 100, CoinLedgerReason.PURCHASE, "IAP_PURCHASE", UUID.randomUUID()));

        // then
        ArgumentCaptor<CoinWallet> walletCaptor = ArgumentCaptor.forClass(CoinWallet.class);
        verify(coinWalletWriter).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().paidBalance()).isEqualTo(100);
        verify(coinLedgerWriter, times(1)).append(org.mockito.ArgumentMatchers.any(CoinLedger.class));
    }

    @Test
    @DisplayName("리워드+유료가 함께 차감되면 버킷별로 원장 2건이 기록된다")
    void givenRewardAndPaid_whenSpend_thenTwoLedgerEntries() {
        // given — 유료100·리워드50에서 80 소모 → 리워드50 + 유료30
        CoinWallet wallet = new CoinWallet(UUID.randomUUID(), userId, 100, 50, null);
        given(coinWalletReader.getByUserId(userId)).willReturn(wallet);

        // when
        coinService.spend(new CoinSpendCommand(
                userId, 80, CoinLedgerReason.MATCH_ENTER, "MATCH", UUID.randomUUID()));

        // then
        ArgumentCaptor<CoinWallet> walletCaptor = ArgumentCaptor.forClass(CoinWallet.class);
        verify(coinWalletWriter).save(walletCaptor.capture());
        assertThat(walletCaptor.getValue().paidBalance()).isEqualTo(70);
        assertThat(walletCaptor.getValue().rewardBalance()).isZero();

        ArgumentCaptor<CoinLedger> ledgerCaptor = ArgumentCaptor.forClass(CoinLedger.class);
        verify(coinLedgerWriter, times(2)).append(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getAllValues())
                .anyMatch(l -> l.bucket() == CoinBucket.REWARD && l.delta() == -50)
                .anyMatch(l -> l.bucket() == CoinBucket.PAID && l.delta() == -30);
    }

    @Test
    @DisplayName("지갑이 없는 사용자가 소모를 시도하면 CoinWalletNotFoundException이 발생한다")
    void givenNoWallet_whenSpend_thenThrows() {
        // given
        given(coinWalletReader.getByUserId(userId)).willThrow(new CoinWalletNotFoundException());

        // when & then
        assertThatThrownBy(() -> coinService.spend(new CoinSpendCommand(
                userId, 10, CoinLedgerReason.CHAT_OPEN, null, null)))
                .isInstanceOf(CoinWalletNotFoundException.class);
    }
}
