package org.son.haruharu.domain.payment.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinRevokeCommand;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.payment.business.command.IapWebhookCommand;
import org.son.haruharu.domain.payment.business.exception.InvalidWebhookPayloadException;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapProductReader;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseReader;
import org.son.haruharu.domain.payment.implement.IapPurchaseWriter;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.payment.business.event.PaymentCompletedEvent;
import org.son.haruharu.domain.payment.implement.IapWebhookEventReader;
import org.son.haruharu.domain.payment.implement.IapWebhookEventWriter;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock private CoinService coinService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentService paymentService;

    private final String userId = "1";
    private final String txId = "TXN-123";
    private final String storeProductId = "org.son.haruharu.coin.100";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                iapProductReader, iapPurchaseReader, iapPurchaseWriter,
                iapWebhookEventReader, iapWebhookEventWriter, coinService, eventPublisher);
    }

    private IapWebhookCommand command(String type) {
        return new IapWebhookCommand("EVT-1", type, userId, storeProductId,
                IapStore.APP_STORE, txId, LocalDateTime.now(), "{}");
    }

    private IapProduct product() {
        return new IapProduct(1L, storeProductId, IapStore.APP_STORE, 100, 1200, "KRW", true);
    }

    private IapPurchase ownedPurchase(Long id) {
        return new IapPurchase(id, Long.parseLong(userId), 1L, storeProductId, txId,
                IapStore.APP_STORE, 100, org.son.haruharu.domain.payment.implement.IapPurchaseStatus.OWNED,
                LocalDateTime.now(), null);
    }

    @Test
    @DisplayName("구매 이벤트 수신 시 OWNED 구매 저장 + PAID 코인 적립이 일어난다")
    void givenPurchaseEvent_whenHandle_thenSavePurchaseAndCreditCoin() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.empty());
        given(iapProductReader.getByStoreProductId(storeProductId)).willReturn(product());
        given(iapPurchaseWriter.save(any())).willReturn(ownedPurchase(1L));

        // when
        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        // then
        verify(iapPurchaseWriter).save(any(IapPurchase.class));
        ArgumentCaptor<CoinCreditCommand> captor = ArgumentCaptor.forClass(CoinCreditCommand.class);
        verify(coinService).credit(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(CoinBucket.PAID);
        assertThat(captor.getValue().reason()).isEqualTo(CoinLedgerReason.PURCHASE);
        assertThat(captor.getValue().amount()).isEqualTo(100);
        verify(iapWebhookEventWriter).append(any());
    }

    @Test
    @DisplayName("신규 구매 이벤트 처리 시 PaymentCompletedEvent를 발행한다")
    void givenPurchaseEvent_whenHandle_thenPublishesPaymentCompletedEvent() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.empty());
        given(iapProductReader.getByStoreProductId(storeProductId)).willReturn(product());
        given(iapPurchaseWriter.save(any())).willReturn(ownedPurchase(1L));

        // when
        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        // then
        ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(Long.parseLong(userId));
    }

    @Test
    @DisplayName("동일 거래 중복 구매 이벤트는 PaymentCompletedEvent를 발행하지 않는다")
    void givenDuplicateTransaction_whenHandlePurchase_thenNoPaymentCompletedEvent() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.of(ownedPurchase(1L)));

        // when
        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        // then
        verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    @DisplayName("환불(CANCELLATION) 이벤트는 PaymentCompletedEvent를 발행하지 않는다")
    void givenCancellationEvent_whenHandle_thenNoPaymentCompletedEvent() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.of(ownedPurchase(1L)));
        given(iapPurchaseWriter.save(any())).willReturn(ownedPurchase(1L));

        // when
        paymentService.handleWebhook(command("CANCELLATION"));

        // then
        verify(eventPublisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    @DisplayName("환불(CANCELLATION) 이벤트 수신 시 구매가 REFUNDED 처리되고 PAID 코인이 회수된다")
    void givenCancellationEvent_whenHandle_thenRefundAndRevokeCoin() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.of(ownedPurchase(1L)));
        given(iapPurchaseWriter.save(any())).willReturn(ownedPurchase(1L));

        // when
        paymentService.handleWebhook(command("CANCELLATION"));

        // then
        verify(iapPurchaseWriter).save(any(IapPurchase.class));
        ArgumentCaptor<CoinRevokeCommand> captor = ArgumentCaptor.forClass(CoinRevokeCommand.class);
        verify(coinService).revoke(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(100);
        verify(coinService, never()).credit(any());
    }

    @Test
    @DisplayName("이미 처리된 이벤트(event.id 중복)는 멱등 처리되어 아무 부수효과가 없다")
    void givenAlreadyProcessedEvent_whenHandle_thenSkip() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(true);

        // when
        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        // then
        verify(iapPurchaseWriter, never()).save(any());
        verify(coinService, never()).credit(any());
        verify(coinService, never()).revoke(any());
        verify(iapWebhookEventWriter, never()).append(any());
    }

    @Test
    @DisplayName("동일 거래(transaction) 중복 구매 이벤트는 코인을 다시 적립하지 않는다")
    void givenDuplicateTransaction_whenHandlePurchase_thenNoDoubleCredit() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.of(ownedPurchase(1L)));

        // when
        paymentService.handleWebhook(command("NON_RENEWING_PURCHASE"));

        // then
        verify(iapPurchaseWriter, never()).save(any());
        verify(coinService, never()).credit(any());
        verify(iapWebhookEventWriter).append(any()); // 이벤트 로그는 남긴다
    }

    @Test
    @DisplayName("구매 이벤트의 app_user_id가 숫자 형식이 아니면 InvalidWebhookPayloadException이 발생한다")
    void givenInvalidAppUserId_whenHandlePurchase_thenThrows() {
        // given
        given(iapWebhookEventReader.isProcessed("EVT-1")).willReturn(false);
        given(iapPurchaseReader.findByStoreTransactionId(txId)).willReturn(Optional.empty());
        IapWebhookCommand invalid = new IapWebhookCommand("EVT-1", "NON_RENEWING_PURCHASE",
                "not-a-uuid", storeProductId, IapStore.APP_STORE, txId, LocalDateTime.now(), "{}");

        // when & then
        assertThatThrownBy(() -> paymentService.handleWebhook(invalid))
                .isInstanceOf(InvalidWebhookPayloadException.class);
    }
}
