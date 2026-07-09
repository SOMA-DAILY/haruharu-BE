package org.son.haruharu.domain.payment.business;

import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinRevokeCommand;
import org.son.haruharu.domain.payment.business.command.IapWebhookCommand;
import org.son.haruharu.domain.payment.business.exception.InvalidWebhookPayloadException;
import org.son.haruharu.domain.payment.business.dto.ProductResponse;
import org.son.haruharu.domain.payment.business.dto.ProductsResponse;
import org.son.haruharu.domain.payment.business.event.PaymentCompletedEvent;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapProductReader;
import org.son.haruharu.domain.payment.implement.IapPurchase;
import org.son.haruharu.domain.payment.implement.IapPurchaseReader;
import org.son.haruharu.domain.payment.implement.IapPurchaseWriter;
import org.son.haruharu.domain.payment.implement.IapWebhookEvent;
import org.son.haruharu.domain.payment.implement.IapWebhookEventReader;
import org.son.haruharu.domain.payment.implement.IapWebhookEventWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final String REF_TYPE = "IAP_PURCHASE";
    private static final String TYPE_PURCHASE = "NON_RENEWING_PURCHASE";
    private static final String TYPE_INITIAL_PURCHASE = "INITIAL_PURCHASE";
    private static final String TYPE_CANCELLATION = "CANCELLATION";

    private final IapProductReader iapProductReader;
    private final IapPurchaseReader iapPurchaseReader;
    private final IapPurchaseWriter iapPurchaseWriter;
    private final IapWebhookEventReader iapWebhookEventReader;
    private final IapWebhookEventWriter iapWebhookEventWriter;
    private final CoinService coinService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(
            IapProductReader iapProductReader,
            IapPurchaseReader iapPurchaseReader,
            IapPurchaseWriter iapPurchaseWriter,
            IapWebhookEventReader iapWebhookEventReader,
            IapWebhookEventWriter iapWebhookEventWriter,
            CoinService coinService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.iapProductReader = iapProductReader;
        this.iapPurchaseReader = iapPurchaseReader;
        this.iapPurchaseWriter = iapPurchaseWriter;
        this.iapWebhookEventReader = iapWebhookEventReader;
        this.iapWebhookEventWriter = iapWebhookEventWriter;
        this.coinService = coinService;
        this.eventPublisher = eventPublisher;
    }

    /** 활성 코인 상품 목록. */
    public ProductsResponse getProducts() {
        List<ProductResponse> products = iapProductReader.findAllActive().stream()
                .map(ProductResponse::from)
                .toList();
        return new ProductsResponse(products);
    }

    /**
     * RevenueCat webhook 처리. eventId로 멱등 보장하고, 이벤트 타입별로 코인 적립/회수를 수행한다.
     * 호출 전 Authorization 검증이 완료되었다고 가정한다.
     */
    @Transactional
    public void handleWebhook(IapWebhookCommand command) {
        if (iapWebhookEventReader.isProcessed(command.eventId())) {
            return;
        }

        Long purchaseId = switch (command.eventType()) {
            case TYPE_PURCHASE, TYPE_INITIAL_PURCHASE -> handlePurchase(command);
            case TYPE_CANCELLATION -> handleCancellation(command);
            default -> null;
        };

        iapWebhookEventWriter.append(IapWebhookEvent.of(
                command.eventId(), command.eventType(), purchaseId, command.rawPayload(), LocalDateTime.now()));
    }

    private Long handlePurchase(IapWebhookCommand command) {
        // 거래 단위 멱등 — 동일 transaction 재수신 시 중복 적립 방지
        Optional<IapPurchase> existing = iapPurchaseReader.findByStoreTransactionId(command.storeTransactionId());
        if (existing.isPresent()) {
            return existing.get().id();
        }

        Long userId = parseUserId(command.appUserId());
        IapProduct product = iapProductReader.getByStoreProductId(command.storeProductId());
        IapPurchase saved = iapPurchaseWriter.save(IapPurchase.owned(
                userId, product.id(), command.storeProductId(), command.storeTransactionId(),
                command.store(), product.coinAmount(), command.purchasedAt()));

        coinService.credit(CoinCreditCommand.purchase(userId, product.coinAmount(), REF_TYPE, saved.id().toString()));
        // 커밋 후 발송 — 결제 완료 통지 푸시 실패가 충전 처리를 롤백시키지 않도록
        eventPublisher.publishEvent(new PaymentCompletedEvent(userId, product.coinAmount()));
        return saved.id();
    }

    private Long parseUserId(String appUserId) {
        if (appUserId == null || appUserId.isBlank()) {
            throw new InvalidWebhookPayloadException();
        }
        try {
            return Long.parseLong(appUserId);
        } catch (NumberFormatException e) {
            throw new InvalidWebhookPayloadException();
        }
    }

    private Long handleCancellation(IapWebhookCommand command) {
        Optional<IapPurchase> found = iapPurchaseReader.findByStoreTransactionId(command.storeTransactionId());
        if (found.isEmpty() || !found.get().isOwned()) {
            return found.map(IapPurchase::id).orElse(null);
        }

        IapPurchase purchase = found.get();
        IapPurchase refunded = iapPurchaseWriter.save(purchase.refunded(LocalDateTime.now()));
        coinService.revoke(new CoinRevokeCommand(
                purchase.userId(), purchase.coinAmount(), REF_TYPE, purchase.id().toString()));
        return refunded.id();
    }
}
