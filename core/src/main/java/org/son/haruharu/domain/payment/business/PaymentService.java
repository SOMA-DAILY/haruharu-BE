package org.son.haruharu.domain.payment.business;

import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.business.command.TicketRevokeCommand;
import org.son.haruharu.domain.payment.business.command.IapWebhookCommand;
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
import org.springframework.beans.factory.annotation.Value;
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
    private static final String ENV_PRODUCTION = "PRODUCTION";

    private final IapProductReader iapProductReader;
    private final IapPurchaseReader iapPurchaseReader;
    private final IapPurchaseWriter iapPurchaseWriter;
    private final IapWebhookEventReader iapWebhookEventReader;
    private final IapWebhookEventWriter iapWebhookEventWriter;
    private final TicketService ticketService;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean acceptSandbox;

    public PaymentService(
            IapProductReader iapProductReader,
            IapPurchaseReader iapPurchaseReader,
            IapPurchaseWriter iapPurchaseWriter,
            IapWebhookEventReader iapWebhookEventReader,
            IapWebhookEventWriter iapWebhookEventWriter,
            TicketService ticketService,
            ApplicationEventPublisher eventPublisher,
            @Value("${revenuecat.accept-sandbox:false}") boolean acceptSandbox
    ) {
        this.iapProductReader = iapProductReader;
        this.iapPurchaseReader = iapPurchaseReader;
        this.iapPurchaseWriter = iapPurchaseWriter;
        this.iapWebhookEventReader = iapWebhookEventReader;
        this.iapWebhookEventWriter = iapWebhookEventWriter;
        this.ticketService = ticketService;
        this.eventPublisher = eventPublisher;
        this.acceptSandbox = acceptSandbox;
    }

    /** 활성 티켓 상품 목록. */
    public ProductsResponse getProducts() {
        List<ProductResponse> products = iapProductReader.findAllActive().stream()
                .map(ProductResponse::from)
                .toList();
        return new ProductsResponse(products);
    }

    /**
     * RevenueCat webhook 처리. eventId로 멱등 보장하고, 이벤트 타입별로 티켓 적립/회수를 수행한다.
     * 호출 전 Authorization 검증이 완료되었다고 가정한다.
     * <p>
     * 다시 받아도 결과가 달라지지 않는 이벤트(샌드박스, 우리 유저가 아닌 구매 등)는 적립 없이 기록만 하고
     * 정상 응답한다 — 실패로 응답하면 RevenueCat이 같은 이벤트를 계속 재전송한다. 반대로 등록되지 않은
     * 상품은 예외로 실패시켜 재시도되게 둔다. 상품 등록 누락은 재시도 사이에 고칠 수 있기 때문이다.
     */
    @Transactional
    public void handleWebhook(IapWebhookCommand command) {
        if (iapWebhookEventReader.isProcessed(command.eventId())) {
            log.info("iap webhook-skip reason=DUPLICATE_EVENT eventId={} type={}",
                    command.eventId(), command.eventType());
            return;
        }
        log.info("iap webhook-received eventId={} type={} env={}",
                command.eventId(), command.eventType(), command.environment());

        Long purchaseId = isAcceptedEnvironment(command)
                ? dispatch(command)
                : ignore(command, "SANDBOX_NOT_ACCEPTED");

        iapWebhookEventWriter.append(IapWebhookEvent.of(
                command.eventId(), command.eventType(), purchaseId, command.rawPayload(), LocalDateTime.now()));
    }

    /**
     * 운영 결제만 받는다. 샌드박스(스토어 테스트 계정) 결제는 돈이 오가지 않으므로 운영에서 적립하면
     * 공짜 티켓이 된다. 환경 값이 비었거나 모르는 값이어도 운영으로 보지 않는다(닫힌 쪽으로 판단).
     * 개발 서버는 revenuecat.accept-sandbox=true 로 샌드박스를 허용한다.
     */
    private boolean isAcceptedEnvironment(IapWebhookCommand command) {
        return acceptSandbox || ENV_PRODUCTION.equals(command.environment());
    }

    private Long dispatch(IapWebhookCommand command) {
        return switch (command.eventType()) {
            case TYPE_PURCHASE, TYPE_INITIAL_PURCHASE -> handlePurchase(command);
            case TYPE_CANCELLATION -> handleCancellation(command);
            // 티켓 적립·회수와 무관한 타입(RENEWAL·EXPIRATION·BILLING_ISSUE 등)은 기록만 하고 넘긴다.
            // 조용히 삼키면 새 타입 유입이나 처리 누락을 눈치채지 못하므로 warn으로 남긴다(#302).
            default -> {
                log.warn("iap webhook-unhandled eventId={} type={} — 기록만 하고 티켓 처리는 하지 않는다",
                        command.eventId(), command.eventType());
                yield null;
            }
        };
    }

    /** 적립 없이 기록만 남기는 이벤트. 사유를 남겨 결제했는데 티켓이 없다는 문의에 추적할 수 있게 한다. */
    private Long ignore(IapWebhookCommand command, String reason) {
        log.warn("iap webhook-ignored reason={} eventId={} type={} env={} txn={}",
                reason, command.eventId(), command.eventType(), command.environment(),
                command.storeTransactionId());
        return null;
    }

    private Long handlePurchase(IapWebhookCommand command) {
        if (command.storeTransactionId() == null || command.storeTransactionId().isBlank()) {
            return ignore(command, "MISSING_TRANSACTION_ID");
        }
        if (command.store() == null) {
            return ignore(command, "UNSUPPORTED_STORE");
        }
        // 거래 단위 멱등 — 동일 transaction 재수신 시 중복 적립 방지
        Optional<IapPurchase> existing = iapPurchaseReader.findByStoreTransactionId(command.storeTransactionId());
        if (existing.isPresent()) {
            log.info("iap webhook-skip reason=DUPLICATE_TRANSACTION eventId={} purchaseId={} txn={}",
                    command.eventId(), existing.get().id(), command.storeTransactionId());
            return existing.get().id();
        }

        // app_user_id 는 앱이 RevenueCat 에 넘긴 우리 user id 다. 로그인 전 익명 구매는 숫자가 아니다.
        Optional<Long> userId = parseUserId(command.appUserId());
        if (userId.isEmpty()) {
            return ignore(command, "UNKNOWN_APP_USER_ID");
        }
        if (!ticketService.hasWallet(userId.get())) {
            return ignore(command, "USER_WITHOUT_WALLET");
        }
        IapProduct product = iapProductReader.getByStoreProductId(command.storeProductId());
        // 구매 시점 지급량을 구매 기록에 남긴다 — 나중에 상품 수량을 바꿔도 환불 회수 기준은 변하지 않는다.
        long tickets = product.ticketAmount();
        LocalDateTime purchasedAt = command.purchasedAt() != null ? command.purchasedAt() : LocalDateTime.now();

        IapPurchase saved = iapPurchaseWriter.save(IapPurchase.owned(
                userId.get(), product.id(), command.storeProductId(), command.storeTransactionId(),
                command.store(), tickets, purchasedAt));
        ticketService.credit(TicketCreditCommand.purchase(userId.get(), tickets, REF_TYPE, saved.id().toString()));
        // 적립은 TicketService 로그(ticket credit ... ref=IAP_PURCHASE:{id})에도 남는다.
        // 여기서는 그 ref를 결제 이벤트·거래와 이어주는 정보를 남겨, 두 로그가 purchaseId로 연결되게 한다.
        log.info("iap purchase userId={} purchaseId={} product={} tickets={} txn={} eventId={}",
                userId.get(), saved.id(), command.storeProductId(), tickets,
                command.storeTransactionId(), command.eventId());
        // 커밋 후 발송 — 결제 완료 통지 푸시 실패가 지급 처리를 롤백시키지 않도록
        eventPublisher.publishEvent(new PaymentCompletedEvent(userId.get(), tickets));
        return saved.id();
    }

    private Optional<Long> parseUserId(String appUserId) {
        if (appUserId == null || appUserId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(appUserId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Long handleCancellation(IapWebhookCommand command) {
        // 구매 행을 잠그고 읽는다 — 같은 거래의 환불 이벤트가 서로 다른 id 로 동시에 와도 뒤에 온 쪽은
        // REFUNDED 를 보고 건너뛴다. 잠그지 않으면 둘 다 OWNED 를 읽고 다른 구매분까지 두 번 깎는다.
        Optional<IapPurchase> found = iapPurchaseReader.findByStoreTransactionIdForUpdate(command.storeTransactionId());
        if (found.isEmpty() || !found.get().isOwned()) {
            // 구매 기록이 없거나 이미 환불된 건 — 재수신이거나 우리가 처리한 적 없는 거래다.
            log.warn("iap refund-skip reason={} eventId={} txn={}",
                    found.isEmpty() ? "PURCHASE_NOT_FOUND" : "ALREADY_REFUNDED",
                    command.eventId(), command.storeTransactionId());
            return found.map(IapPurchase::id).orElse(null);
        }
        IapPurchase purchase = found.get();
        // 남은 잔액 한도에서만 회수한다. 이미 써버려 못 깎은 수량은 구매 기록에 남긴다(#492).
        // 탈퇴해 지갑이 없는 유저는 회수할 잔액이 없다 — 실패로 응답해 재시도시키지 않고 전량 미회수로 남긴다.
        long revoked = ticketService.hasWallet(purchase.userId())
                ? ticketService.revoke(new TicketRevokeCommand(
                        purchase.userId(), purchase.ticketAmount(), REF_TYPE, purchase.id().toString()))
                : 0L;
        IapPurchase refunded = iapPurchaseWriter.save(purchase.refunded(LocalDateTime.now(), revoked));
        // 환불 회수는 분쟁 문의가 가장 잦은 경로다. 회수 결과 잔액은 TicketService revoke 로그에 남는다.
        log.info("iap refund userId={} purchaseId={} tickets={} revoked={} unrevoked={} txn={} eventId={}",
                purchase.userId(), purchase.id(), purchase.ticketAmount(), revoked,
                refunded.unrevokedTicketAmount(), command.storeTransactionId(), command.eventId());
        return refunded.id();
    }
}
