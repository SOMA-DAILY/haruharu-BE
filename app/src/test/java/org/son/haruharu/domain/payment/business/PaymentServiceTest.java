package org.son.haruharu.domain.payment.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.payment.storage.exception.IapProductNotFoundException;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.business.command.TicketRevokeCommand;
import org.son.haruharu.domain.ticket.implement.TicketLedgerReason;
import org.son.haruharu.domain.payment.business.command.IapWebhookCommand;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapProductReader;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseReader;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.payment.implement.IapPurchaseWriter;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.payment.business.event.PaymentCompletedEvent;
import org.son.haruharu.domain.payment.implement.IapWebhookEvent;
import org.son.haruharu.domain.payment.implement.IapWebhookEventReader;
import org.son.haruharu.domain.payment.implement.IapWebhookEventWriter;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private IapProductReader iapProductReader;
    @Mock private IapPurchaseReader iapPurchaseReader;
    @Mock private IapPurchaseWriter iapPurchaseWriter;
    @Mock private IapWebhookEventReader iapWebhookEventReader;
    @Mock private IapWebhookEventWriter iapWebhookEventWriter;
    @Mock private TicketService ticketService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentService paymentService;

    private final String userId = "1";
    private final String txId = "TXN-123";
    private final String storeProductId = "org.son.haruharu.ticket.1";

    @BeforeEach
    void setUp() {
        paymentService = service(false);
    }

    private PaymentService service(boolean acceptSandbox) {
        return new PaymentService(
                iapProductReader, iapPurchaseReader, iapPurchaseWriter,
                iapWebhookEventReader, iapWebhookEventWriter, ticketService, eventPublisher, acceptSandbox);
    }

    private IapWebhookCommand command(String type) {
        return command(type, userId, IapStore.APP_STORE, txId, LocalDateTime.now(), "PRODUCTION");
    }

    private IapWebhookCommand command(String type, String appUserId, IapStore store, String txn,
                                      LocalDateTime purchasedAt, String environment) {
        return new IapWebhookCommand("EVT-1", type, appUserId, storeProductId,
                store, txn, purchasedAt, environment, "{}");
    }

    private IapProduct product() {
        return new IapProduct(1L, storeProductId, IapStore.APP_STORE, 2, 7900, "KRW", true);
    }

    private IapPurchase ownedPurchase(Long id) {
        return new IapPurchase(id, Long.parseLong(userId), 1L, storeProductId, txId,
                IapStore.APP_STORE, 2, 0, IapPurchaseStatus.OWNED, LocalDateTime.now(), null);
    }

    /** 신규 구매가 끝까지 처리되는 기본 상황. */
    private void givenPurchasable() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.empty());
        given(ticketService.hasWallet(1L)).willReturn(true);
        given(iapProductReader.getByStoreProductId(storeProductId)).willReturn(product());
        given(iapPurchaseWriter.save(any())).willReturn(ownedPurchase(1L));
    }

    private void thenIgnoredButRecorded() {
        verify(iapPurchaseWriter, never()).save(any());
        verify(ticketService, never()).credit(any());
        verify(ticketService, never()).revoke(any());
        verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
        // 처리하지 않아도 이벤트는 남겨 재수신 시 멱등이 성립한다.
        ArgumentCaptor<IapWebhookEvent> captor = ArgumentCaptor.forClass(IapWebhookEvent.class);
        verify(iapWebhookEventWriter).append(captor.capture());
        assertThat(captor.getValue().purchaseId()).isNull();
    }

    @Test
    @DisplayName("구매 이벤트 수신 시 OWNED 구매 저장 + 상품의 티켓 수량만큼 적립이 일어난다")
    void givenPurchaseEvent_whenHandle_thenSavePurchaseAndCreditTicket() {
        givenPurchasable();

        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        ArgumentCaptor<IapPurchase> purchaseCaptor = ArgumentCaptor.forClass(IapPurchase.class);
        verify(iapPurchaseWriter).save(purchaseCaptor.capture());
        assertThat(purchaseCaptor.getValue().ticketAmount()).isEqualTo(2);
        assertThat(purchaseCaptor.getValue().unrevokedTicketAmount()).isZero();
        ArgumentCaptor<TicketCreditCommand> captor = ArgumentCaptor.forClass(TicketCreditCommand.class);
        verify(ticketService).credit(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo(TicketLedgerReason.PURCHASE);
        assertThat(captor.getValue().amount()).isEqualTo(2);
        verify(iapWebhookEventWriter).append(any());
    }

    @Test
    @DisplayName("신규 구매 이벤트 처리 시 지급 수량을 담아 PaymentCompletedEvent를 발행한다")
    void givenPurchaseEvent_whenHandle_thenPublishesPaymentCompletedEvent() {
        givenPurchasable();

        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(Long.parseLong(userId));
        assertThat(captor.getValue().ticketAmount()).isEqualTo(2);
    }

    @Test
    @DisplayName("구매 시각이 없는 이벤트는 수신 시각으로 구매를 기록한다")
    void givenNoPurchasedAt_whenHandlePurchase_thenUsesReceivedTime() {
        givenPurchasable();

        paymentService.handleWebhook(
                command("NON_RENEWING_PURCHASE", userId, IapStore.APP_STORE, txId, null, "PRODUCTION"));

        ArgumentCaptor<IapPurchase> captor = ArgumentCaptor.forClass(IapPurchase.class);
        verify(iapPurchaseWriter).save(captor.capture());
        assertThat(captor.getValue().purchasedAt()).isNotNull();
        verify(ticketService).credit(any());
    }

    @Test
    @DisplayName("동일 거래 중복 구매 이벤트는 PaymentCompletedEvent를 발행하지 않는다")
    void givenDuplicateTransaction_whenHandlePurchase_thenNoPaymentCompletedEvent() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.of(ownedPurchase(1L)));

        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    @DisplayName("환불(CANCELLATION)로 전량 회수되면 구매가 REFUNDED 가 되고 미회수 수량은 0이다")
    void givenCancellationEvent_whenFullyRevoked_thenRefundedWithoutShortfall() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionIdForUpdate(txId)).willReturn(Optional.of(ownedPurchase(1L)));
        given(ticketService.hasWallet(1L)).willReturn(true);
        given(ticketService.revoke(any())).willReturn(2L);
        given(iapPurchaseWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentService.handleWebhook(command("CANCELLATION"));

        ArgumentCaptor<TicketRevokeCommand> revokeCaptor = ArgumentCaptor.forClass(TicketRevokeCommand.class);
        verify(ticketService).revoke(revokeCaptor.capture());
        assertThat(revokeCaptor.getValue().amount()).isEqualTo(2);
        ArgumentCaptor<IapPurchase> captor = ArgumentCaptor.forClass(IapPurchase.class);
        verify(iapPurchaseWriter).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(IapPurchaseStatus.REFUNDED);
        assertThat(captor.getValue().refundedAt()).isNotNull();
        assertThat(captor.getValue().unrevokedTicketAmount()).isZero();
        verify(ticketService, never()).credit(any());
        verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    @DisplayName("이미 사용한 티켓이 환불되면 회수하지 못한 수량을 구매 기록에 남긴다")
    void givenCancellationEvent_whenPartiallyRevoked_thenRecordsUnrevokedAmount() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionIdForUpdate(txId)).willReturn(Optional.of(ownedPurchase(1L)));
        given(ticketService.hasWallet(1L)).willReturn(true);
        // 2장 구매 후 2장 모두 써버려 회수 0
        given(ticketService.revoke(any())).willReturn(0L);
        given(iapPurchaseWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentService.handleWebhook(command("CANCELLATION"));

        ArgumentCaptor<IapPurchase> captor = ArgumentCaptor.forClass(IapPurchase.class);
        verify(iapPurchaseWriter).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(IapPurchaseStatus.REFUNDED);
        assertThat(captor.getValue().unrevokedTicketAmount()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 환불된 구매의 환불 이벤트가 다시 오면 또 회수하지 않는다")
    void givenAlreadyRefunded_whenCancellation_thenNoSecondRevoke() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionIdForUpdate(txId))
                .willReturn(Optional.of(ownedPurchase(1L).refunded(LocalDateTime.now(), 2)));

        paymentService.handleWebhook(command("CANCELLATION"));

        verify(ticketService, never()).revoke(any());
        verify(iapPurchaseWriter, never()).save(any());
        verify(iapWebhookEventWriter).append(any());
    }

    @Test
    @DisplayName("탈퇴해 지갑이 없는 유저의 구매가 환불되면 회수 없이 REFUNDED 로 남기고 전량을 미회수로 기록한다")
    void givenUserWithoutWallet_whenCancellation_thenRefundedWithFullShortfall() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionIdForUpdate(txId)).willReturn(Optional.of(ownedPurchase(1L)));
        given(ticketService.hasWallet(1L)).willReturn(false);
        given(iapPurchaseWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentService.handleWebhook(command("CANCELLATION"));

        verify(ticketService, never()).revoke(any());
        ArgumentCaptor<IapPurchase> captor = ArgumentCaptor.forClass(IapPurchase.class);
        verify(iapPurchaseWriter).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(IapPurchaseStatus.REFUNDED);
        assertThat(captor.getValue().unrevokedTicketAmount()).isEqualTo(2);
        verify(iapWebhookEventWriter).append(any());
    }

    @Test
    @DisplayName("이미 처리된 이벤트(event.id 중복)는 멱등 처리되어 아무 부수효과가 없다")
    void givenAlreadyProcessedEvent_whenHandle_thenSkip() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(true);

        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        verify(iapPurchaseWriter, never()).save(any());
        verify(ticketService, never()).credit(any());
        verify(ticketService, never()).revoke(any());
        verify(iapWebhookEventWriter, never()).append(any());
    }

    @Test
    @DisplayName("동일 거래(transaction) 중복 구매 이벤트는 티켓을 다시 적립하지 않는다")
    void givenDuplicateTransaction_whenHandlePurchase_thenNoDoubleCredit() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.of(ownedPurchase(1L)));

        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        verify(iapPurchaseWriter, never()).save(any());
        verify(ticketService, never()).credit(any());
        verify(iapWebhookEventWriter).append(any()); // 이벤트 로그는 남긴다
    }

    @Test
    @DisplayName("샌드박스 구매는 기본 설정에서 적립하지 않고 기록만 남긴다")
    void givenSandboxPurchase_whenSandboxNotAccepted_thenIgnoredButRecorded() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);

        paymentService.handleWebhook(
                command("NON_RENEWING_PURCHASE", userId, IapStore.APP_STORE, txId, LocalDateTime.now(), "SANDBOX"));

        thenIgnoredButRecorded();
    }

    @Test
    @DisplayName("환경 값이 없는 이벤트도 운영 결제로 보지 않는다")
    void givenNoEnvironment_whenSandboxNotAccepted_thenIgnoredButRecorded() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);

        paymentService.handleWebhook(
                command("NON_RENEWING_PURCHASE", userId, IapStore.APP_STORE, txId, LocalDateTime.now(), null));

        thenIgnoredButRecorded();
    }

    @Test
    @DisplayName("샌드박스 허용 설정(개발 서버)에서는 샌드박스 구매도 적립한다")
    void givenSandboxPurchase_whenSandboxAccepted_thenCredits() {
        givenPurchasable();

        service(true).handleWebhook(
                command("NON_RENEWING_PURCHASE", userId, IapStore.APP_STORE, txId, LocalDateTime.now(), "SANDBOX"));

        verify(ticketService).credit(any());
    }

    @Test
    @DisplayName("app_user_id가 우리 유저 id 형식이 아니면(익명 구매) 적립하지 않고 기록만 남긴다")
    void givenNonNumericAppUserId_whenHandlePurchase_thenIgnoredButRecorded() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.empty());

        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE", "$RCAnonymousID:abc",
                IapStore.APP_STORE, txId, LocalDateTime.now(), "PRODUCTION"));

        thenIgnoredButRecorded();
        verify(ticketService, never()).hasWallet(anyLong());
    }

    @Test
    @DisplayName("지갑이 없는 유저(탈퇴·미가입)의 구매는 적립하지 않고 기록만 남긴다")
    void givenUserWithoutWallet_whenHandlePurchase_thenIgnoredButRecorded() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.empty());
        given(ticketService.hasWallet(1L)).willReturn(false);

        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        thenIgnoredButRecorded();
    }

    @Test
    @DisplayName("지원하지 않는 스토어의 구매는 적립하지 않고 기록만 남긴다")
    void givenUnsupportedStore_whenHandlePurchase_thenIgnoredButRecorded() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);

        paymentService.handleWebhook(
                command("NON_RENEWING_PURCHASE", userId, null, txId, LocalDateTime.now(), "PRODUCTION"));

        thenIgnoredButRecorded();
    }

    @Test
    @DisplayName("거래 id가 없는 구매 이벤트는 적립하지 않고 기록만 남긴다")
    void givenNoTransactionId_whenHandlePurchase_thenIgnoredButRecorded() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);

        paymentService.handleWebhook(
                command("NON_RENEWING_PURCHASE", userId, IapStore.APP_STORE, " ", LocalDateTime.now(), "PRODUCTION"));

        thenIgnoredButRecorded();
    }

    @Test
    @DisplayName("등록되지 않은 상품의 구매는 실패로 응답해 재시도되게 한다 — 상품 등록 누락은 고칠 수 있는 문제다")
    void givenUnknownProduct_whenHandlePurchase_thenThrowsForRetry() {
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.empty());
        given(ticketService.hasWallet(1L)).willReturn(true);
        given(iapProductReader.getByStoreProductId(storeProductId))
                .willThrow(new IapProductNotFoundException(storeProductId));

        assertThatThrownBy(() -> paymentService.handleWebhook(command("NON_RENEWING_PURCHASE")))
                .isInstanceOf(IapProductNotFoundException.class);

        verify(ticketService, never()).credit(any());
        verify(iapWebhookEventWriter, never()).append(any());
    }
}
