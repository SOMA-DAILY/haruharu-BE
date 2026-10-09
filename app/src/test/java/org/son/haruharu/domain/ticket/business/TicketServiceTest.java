package org.son.haruharu.domain.ticket.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.business.command.TicketRevokeCommand;
import org.son.haruharu.domain.ticket.business.command.TicketSpendCommand;
import org.son.haruharu.domain.ticket.business.dto.WalletResponse;
import org.son.haruharu.domain.ticket.implement.TicketLedger;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReader;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;
import org.son.haruharu.domain.ticket.implement.TicketLedgerWriter;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.ticket.implement.TicketWalletReader;
import org.son.haruharu.domain.ticket.implement.TicketWalletWriter;
import org.son.haruharu.domain.ticket.implement.exception.InsufficientTicketException;
import org.son.haruharu.domain.ticket.storage.exception.TicketWalletNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketWalletReader ticketWalletReader;
    @Mock private TicketWalletWriter ticketWalletWriter;
    @Mock private TicketLedgerReader ticketLedgerReader;
    @Mock private TicketLedgerWriter ticketLedgerWriter;

    private TicketService ticketService;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketWalletReader, ticketWalletWriter, ticketLedgerReader, ticketLedgerWriter);
    }

    private TicketWallet wallet(long balance) {
        return new TicketWallet(1L, userId, balance, null);
    }

    private TicketLedger appendedLedger() {
        ArgumentCaptor<TicketLedger> captor = ArgumentCaptor.forClass(TicketLedger.class);
        verify(ticketLedgerWriter).append(captor.capture());
        return captor.getValue();
    }

    private TicketWallet savedWallet() {
        ArgumentCaptor<TicketWallet> captor = ArgumentCaptor.forClass(TicketWallet.class);
        verify(ticketWalletWriter).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("지갑이 없는 사용자의 잔액 조회는 0을 반환한다")
    void givenNoWallet_whenGetWallet_thenZeroBalance() {
        given(ticketWalletReader.findByUserId(userId)).willReturn(Optional.empty());

        WalletResponse response = ticketService.getWallet(userId);

        assertThat(response.balance()).isZero();
    }

    @Test
    @DisplayName("적립 시 지갑 잔액 증가와 원장 1건 기록이 함께 일어난다")
    void givenWallet_whenCredit_thenSaveWalletAndAppendLedger() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet(0));

        ticketService.credit(TicketCreditCommand.purchase(userId, 1, "IAP_PURCHASE", "ref-1"));

        assertThat(savedWallet().balance()).isEqualTo(1);
        TicketLedger ledger = appendedLedger();
        assertThat(ledger.reason()).isEqualTo(TicketLedgerReason.PURCHASE);
        assertThat(ledger.delta()).isEqualTo(1);
        assertThat(ledger.balanceAfter()).isEqualTo(1);
    }

    @Test
    @DisplayName("운영진 지급은 ADMIN_GRANT 사유로 지급한 어드민 id와 메모를 원장에 남긴다")
    void givenAdminGrant_whenCredit_thenLedgerKeepsAdminAndMemo() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet(1));

        ticketService.credit(TicketCreditCommand.adminGrant(userId, 2, 77L, "CS 보상"));

        assertThat(savedWallet().balance()).isEqualTo(3);
        TicketLedger ledger = appendedLedger();
        assertThat(ledger.reason()).isEqualTo(TicketLedgerReason.ADMIN_GRANT);
        assertThat(ledger.refType()).isEqualTo("ADMIN");
        assertThat(ledger.refId()).isEqualTo("77");
        assertThat(ledger.memo()).isEqualTo("CS 보상");
        assertThat(ledger.balanceAfter()).isEqualTo(3);
    }

    @Test
    @DisplayName("사용 시 잔액을 차감하고 USE 사유로 원장 1건을 기록한다")
    void givenEnoughBalance_whenSpend_thenDeductsAndAppendsLedger() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet(2));

        ticketService.spend(new TicketSpendCommand(userId, 1, "ROOM", "ref-2"));

        assertThat(savedWallet().balance()).isEqualTo(1);
        TicketLedger ledger = appendedLedger();
        assertThat(ledger.reason()).isEqualTo(TicketLedgerReason.USE);
        assertThat(ledger.delta()).isEqualTo(-1);
        assertThat(ledger.balanceAfter()).isEqualTo(1);
    }

    @Test
    @DisplayName("잔액이 부족하면 사용이 거절되고 지갑·원장은 바뀌지 않는다")
    void givenInsufficientBalance_whenSpend_thenThrowsWithoutChange() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet(0));

        assertThatThrownBy(() -> ticketService.spend(new TicketSpendCommand(userId, 1, "ROOM", "ref-2")))
                .isInstanceOf(InsufficientTicketException.class);

        verify(ticketWalletWriter, never()).save(any());
        verify(ticketLedgerWriter, never()).append(any());
    }

    @Test
    @DisplayName("환불 회수는 잔액이 충분하면 전량 차감하고 회수 수량을 돌려준다")
    void givenEnoughBalance_whenRevoke_thenRevokesAll() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet(3));

        long revoked = ticketService.revoke(new TicketRevokeCommand(userId, 1, "IAP_PURCHASE", "ref-x"));

        assertThat(revoked).isEqualTo(1);
        assertThat(savedWallet().balance()).isEqualTo(2);
        TicketLedger ledger = appendedLedger();
        assertThat(ledger.reason()).isEqualTo(TicketLedgerReason.REFUND_REVOKE);
        assertThat(ledger.delta()).isEqualTo(-1);
        assertThat(ledger.balanceAfter()).isEqualTo(2);
    }

    @Test
    @DisplayName("환불 회수는 잔액이 모자라면 남은 만큼만 차감한다 — 잔액은 0, 원장에는 실제 차감량만 남는다")
    void givenInsufficientBalance_whenRevoke_thenStopsAtZeroAndRecordsActualAmount() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet(1));

        long revoked = ticketService.revoke(new TicketRevokeCommand(userId, 3, "IAP_PURCHASE", "ref-x"));

        assertThat(revoked).isEqualTo(1);
        assertThat(savedWallet().balance()).isZero();
        TicketLedger ledger = appendedLedger();
        assertThat(ledger.delta()).isEqualTo(-1);
        assertThat(ledger.balanceAfter()).isZero();
    }

    @Test
    @DisplayName("이미 다 써버린 뒤 환불되면 회수 수량 0으로 환불 사실만 원장에 남긴다")
    void givenZeroBalance_whenRevoke_thenRecordsZeroDelta() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willReturn(wallet(0));

        long revoked = ticketService.revoke(new TicketRevokeCommand(userId, 1, "IAP_PURCHASE", "ref-x"));

        assertThat(revoked).isZero();
        TicketLedger ledger = appendedLedger();
        assertThat(ledger.reason()).isEqualTo(TicketLedgerReason.REFUND_REVOKE);
        assertThat(ledger.delta()).isZero();
        assertThat(ledger.balanceAfter()).isZero();
    }

    @Test
    @DisplayName("지갑이 없는 사용자가 사용을 시도하면 TicketWalletNotFoundException이 발생한다")
    void givenNoWallet_whenSpend_thenThrows() {
        given(ticketWalletReader.getByUserIdForUpdate(userId)).willThrow(new TicketWalletNotFoundException());

        assertThatThrownBy(() -> ticketService.spend(new TicketSpendCommand(userId, 1, null, null)))
                .isInstanceOf(TicketWalletNotFoundException.class);
    }

    @Test
    @DisplayName("살아 있는 지갑이 있을 때만 hasWallet 이 true 다")
    void givenWalletPresence_whenHasWallet_thenReflectsIt() {
        given(ticketWalletReader.findByUserId(userId)).willReturn(Optional.of(wallet(0)));
        given(ticketWalletReader.findByUserId(2L)).willReturn(Optional.empty());

        assertThat(ticketService.hasWallet(userId)).isTrue();
        assertThat(ticketService.hasWallet(2L)).isFalse();
    }

    @Test
    @DisplayName("회원 탈퇴 시 티켓 지갑을 soft-delete 위임한다")
    void givenUserId_whenDeleteWallet_thenDelegatesSoftDelete() {
        ticketService.deleteWallet(userId);

        verify(ticketWalletWriter).deleteByUserId(userId);
    }

    @Test
    @DisplayName("PURCHASE 원장이 있으면 결제 이력 있음으로 판정한다(#277)")
    void givenPurchaseLedger_whenHasPurchaseHistory_thenTrue() {
        given(ticketLedgerReader.existsByUserIdAndReason(userId, TicketLedgerReason.PURCHASE)).willReturn(true);

        assertThat(ticketService.hasPurchaseHistory(userId)).isTrue();
    }

    @Test
    @DisplayName("PURCHASE 원장이 없으면 결제 이력 없음으로 판정한다(#277)")
    void givenNoPurchaseLedger_whenHasPurchaseHistory_thenFalse() {
        given(ticketLedgerReader.existsByUserIdAndReason(userId, TicketLedgerReason.PURCHASE)).willReturn(false);

        assertThat(ticketService.hasPurchaseHistory(userId)).isFalse();
    }
}
