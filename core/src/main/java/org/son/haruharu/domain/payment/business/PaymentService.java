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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 감사 로그 방침(#302): 돈이 오가는 경로라 webhook 수신·적립·회수·멱등 skip을 모두 남긴다.
 * rawPayload는 남기지 않는다 — 이미 iap_webhook_event.payload에 원문이 있고 구매자 정보가 섞일 수 있다.
 * 인증 실패는 GlobalExceptionHandler가 401 warn으로 남기므로 여기서 중복 기록하지 않는다.
 */
@Slf4j
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
            log.info("iap webhook-skip reason=DUPLICATE_EVENT eventId={} type={}",
                    command.eventId(), command.eventType());
            return;
        }
        log.info("iap webhook-received eventId={} type={}", command.eventId(), command.eventType());

        Long purchaseId = switch (command.eventType()) {
            case TYPE_PURCHASE, TYPE_INITIAL_PURCHASE -> handlePurchase(command);
            case TYPE_CANCELLATION -> handleCancellation(command);
            // 코인 적립·회수와 무관한 타입(RENEWAL·EXPIRATION·BILLING_ISSUE 등)은 기록만 하고 넘긴다.
            // 조용히 삼키면 새 타입 유입이나 처리 누락을 눈치채지 못하므로 warn으로 남긴다(#302).
            default -> {
                log.warn("iap webhook-unhandled eventId={} type={} — 기록만 하고 코인 처리는 하지 않는다",
                        command.eventId(), command.eventType());
                yield null;
            }
        };

        iapWebhookEventWriter.append(IapWebhookEvent.of(
                command.eventId(), command.eventType(), purchaseId, command.rawPayload(), LocalDateTime.now()));
    }

    private Long handlePurchase(IapWebhookCommand command) {
        // 거래 단위 멱등 — 동일 transaction 재수신 시 중복 적립 방지
        Optional<IapPurchase> existing = iapPurchaseReader.findByStoreTransactionId(command.storeTransactionId());
        if (existing.isPresent()) {
            log.info("iap webhook-skip reason=DUPLICATE_TRANSACTION eventId={} purchaseId={} txn={}",
                    command.eventId(), existing.get().id(), command.storeTransactionId());
            return existing.get().id();
        }

        Long userId = parseUserId(command.appUserId());
        IapProduct product = iapProductReader.getByStoreProductId(command.storeProductId());
        // 실제 지급 = 기본 + 보너스. 구매 기록에 총량을 남겨 환불 회수도 총량 기준이 되게 한다.
        long totalCoins = product.totalCoinAmount();
        IapPurchase saved = iapPurchaseWriter.save(IapPurchase.owned(
                userId, product.id(), command.storeProductId(), command.storeTransactionId(),
                command.store(), totalCoins, command.purchasedAt()));

        coinService.credit(CoinCreditCommand.purchase(userId, totalCoins, REF_TYPE, saved.id().toString()));
        // 적립은 CoinService 로그(coin credit ... ref=IAP_PURCHASE:{id})에도 남는다.
        // 여기서는 그 ref를 결제 이벤트·거래와 이어주는 정보를 남겨, 두 로그가 purchaseId로 연결되게 한다.
        log.info("iap purchase userId={} purchaseId={} product={} coins={} txn={} eventId={}",
                userId, saved.id(), command.storeProductId(), totalCoins,
                command.storeTransactionId(), command.eventId());
        // 커밋 후 발송 — 결제 완료 통지 푸시 실패가 충전 처리를 롤백시키지 않도록
        eventPublisher.publishEvent(new PaymentCompletedEvent(userId, totalCoins));
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
            // 구매 기록이 없거나 이미 환불된 건 — 재수신이거나 우리가 처리한 적 없는 거래다.
            log.warn("iap refund-skip reason={} eventId={} txn={}",
                    found.isEmpty() ? "PURCHASE_NOT_FOUND" : "ALREADY_REFUNDED",
                    command.eventId(), command.storeTransactionId());
            return found.map(IapPurchase::id).orElse(null);
        }

        IapPurchase purchase = found.get();
        IapPurchase refunded = iapPurchaseWriter.save(purchase.refunded(LocalDateTime.now()));
        coinService.revoke(new CoinRevokeCommand(
                purchase.userId(), purchase.coinAmount(), REF_TYPE, purchase.id().toString()));
        // 환불 회수는 분쟁 문의가 가장 잦은 경로다. 회수 결과 잔액은 CoinService revoke 로그에 남는다.
        log.info("iap refund userId={} purchaseId={} coins={} txn={} eventId={}",
                purchase.userId(), purchase.id(), purchase.coinAmount(),
                command.storeTransactionId(), command.eventId());
        return refunded.id();
    }
}
