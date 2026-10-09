package org.son.haruharu.domain.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketSpendCommand;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.payment.storage.IapProductEntity;
import org.son.haruharu.domain.payment.storage.IapProductJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RevenueCat webhook을 실제 HTTP 경로로 검증한다(#302) — 시크릿 검증 → JSON 파싱 → 트랜잭션 → 티켓 적립.
 *
 * <p>기존 PaymentServiceTest는 전부 mock 기반이라 인증·파싱 계층을 타지 않는다. 이 테스트는
 * webhook의 관문(공유 시크릿)이 실제로 막는지, 정상 요청이 끝까지 흘러 잔액이 되는지를 확인한다.
 * 결제 진입점은 외부에 열린 유일한 비JWT 엔드포인트라 인증 회귀가 그대로 금전 사고가 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IapWebhookApiIntegrationTest {

    private static final String SECRET = "test-only-webhook-secret";
    private static final String PRODUCT_ID = "org.son.haruharu.ticket.1";
    private static final Long USER_ID = 950L;

    @Autowired MockMvc mockMvc;
    @Autowired IapProductJpaRepository iapProductJpaRepository;
    @Autowired TicketService ticketService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM iap_webhook_event");
        jdbcTemplate.update("DELETE FROM iap_purchase");
        jdbcTemplate.update("DELETE FROM iap_product");
        jdbcTemplate.update("DELETE FROM ticket_ledger");
        jdbcTemplate.update("DELETE FROM ticket_wallet");

        iapProductJpaRepository.save(IapProductEntity.from(new IapProduct(
                null, PRODUCT_ID, IapStore.APP_STORE, 2L, 7900, "KRW", true)));
        // 잔액 변경은 지갑 존재를 전제한다(#298).
        ticketService.createWallet(USER_ID);
    }

    private String payload(String eventId, String type, String txn) {
        return payload(eventId, type, txn, "PRODUCTION");
    }

    private String payload(String eventId, String type, String txn, String environment) {
        return """
                {"event":{"id":"%s","type":"%s","app_user_id":"%d","product_id":"%s",
                "store":"APP_STORE","transaction_id":"%s","purchased_at_ms":1750000000000,
                "environment":"%s"}}
                """.formatted(eventId, type, USER_ID, PRODUCT_ID, txn, environment);
    }

    private void send(String body) throws Exception {
        mockMvc.perform(post("/api/v1/iap/webhook")
                        .header(HttpHeaders.AUTHORIZATION, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("올바른 시크릿으로 구매 webhook을 보내면 티켓이 적립된다")
    void givenValidSecret_whenPurchaseWebhook_thenTicketsCredited() throws Exception {
        mockMvc.perform(post("/api/v1/iap/webhook")
                        .header(HttpHeaders.AUTHORIZATION, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("EVT-1", "NON_RENEWING_PURCHASE", "TXN-1")))
                .andExpect(status().isOk());

        assertThat(ticketService.getWallet(USER_ID).balance()).isEqualTo(2L);
        assertThat(purchaseCount()).isEqualTo(1L);
        assertThat(webhookEventCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("시크릿이 없으면 401을 반환하고 아무것도 처리하지 않는다")
    void givenNoSecret_whenWebhook_thenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/iap/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("EVT-2", "NON_RENEWING_PURCHASE", "TXN-2")))
                .andExpect(status().isUnauthorized());

        assertThat(ticketService.getWallet(USER_ID).balance()).isZero();
        assertThat(webhookEventCount()).isZero();
    }

    @Test
    @DisplayName("시크릿이 틀리면 401을 반환하고 아무것도 처리하지 않는다")
    void givenWrongSecret_whenWebhook_thenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/iap/webhook")
                        .header(HttpHeaders.AUTHORIZATION, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("EVT-3", "NON_RENEWING_PURCHASE", "TXN-3")))
                .andExpect(status().isUnauthorized());

        assertThat(ticketService.getWallet(USER_ID).balance()).isZero();
        assertThat(webhookEventCount()).isZero();
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 보내도 티켓은 한 번만 적립된다(멱등)")
    void givenSameEventTwice_whenWebhook_thenCreditsOnce() throws Exception {
        String body = payload("EVT-4", "NON_RENEWING_PURCHASE", "TXN-4");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/iap/webhook")
                            .header(HttpHeaders.AUTHORIZATION, SECRET)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        assertThat(ticketService.getWallet(USER_ID).balance()).isEqualTo(2L);
        assertThat(purchaseCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("환불 webhook을 받으면 적립했던 티켓이 회수된다")
    void givenPurchased_whenCancellationWebhook_thenTicketsRevoked() throws Exception {
        mockMvc.perform(post("/api/v1/iap/webhook")
                        .header(HttpHeaders.AUTHORIZATION, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("EVT-5", "NON_RENEWING_PURCHASE", "TXN-5")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/iap/webhook")
                        .header(HttpHeaders.AUTHORIZATION, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("EVT-6", "CANCELLATION", "TXN-5")))
                .andExpect(status().isOk());

        // 적립 2 → 회수 2 → 0
        assertThat(ticketService.getWallet(USER_ID).balance()).isZero();
        assertThat(unrevokedOf("TXN-5")).isZero();
    }

    @Test
    @DisplayName("이미 사용한 티켓이 환불되면 잔액은 0에서 멈추고 미회수 수량이 구매 기록에 남는다")
    void givenTicketsAlreadyUsed_whenCancellationWebhook_thenBalanceStopsAtZero() throws Exception {
        send(payload("EVT-8", "NON_RENEWING_PURCHASE", "TXN-8"));
        // 2장 중 1장을 써버린 뒤 환불된다.
        ticketService.spend(new TicketSpendCommand(USER_ID, 1, "ROOM", "1"));

        send(payload("EVT-9", "CANCELLATION", "TXN-8"));

        assertThat(ticketService.getWallet(USER_ID).balance()).isZero();
        assertThat(unrevokedOf("TXN-8")).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM iap_purchase WHERE store_transaction_id = 'TXN-8'", String.class))
                .isEqualTo("REFUNDED");
        // 원장: 구매 +2, 사용 -1, 회수 -1(실제 차감량) → 합계 0 = 잔액
        assertThat(jdbcTemplate.queryForObject(
                "SELECT delta FROM ticket_ledger WHERE reason = 'REFUND_REVOKE'", Long.class)).isEqualTo(-1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(delta), 0) FROM ticket_ledger WHERE user_id = ?", Long.class, USER_ID)).isZero();
    }

    @Test
    @DisplayName("샌드박스 구매 webhook은 200으로 받되 적립하지 않고 기록만 남긴다")
    void givenSandboxEvent_whenPurchaseWebhook_thenNotCredited() throws Exception {
        send(payload("EVT-10", "NON_RENEWING_PURCHASE", "TXN-10", "SANDBOX"));

        assertThat(ticketService.getWallet(USER_ID).balance()).isZero();
        assertThat(purchaseCount()).isZero();
        assertThat(webhookEventCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("등록되지 않은 상품의 구매 webhook은 실패로 응답하고 아무것도 남기지 않는다(재시도 대상)")
    void givenUnknownProduct_whenPurchaseWebhook_thenFailsWithoutRecord() throws Exception {
        jdbcTemplate.update("DELETE FROM iap_product");

        mockMvc.perform(post("/api/v1/iap/webhook")
                        .header(HttpHeaders.AUTHORIZATION, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("EVT-11", "NON_RENEWING_PURCHASE", "TXN-11")))
                .andExpect(status().isNotFound());

        assertThat(ticketService.getWallet(USER_ID).balance()).isZero();
        assertThat(webhookEventCount()).isZero();
    }

    @Test
    @DisplayName("티켓과 무관한 이벤트 타입은 기록만 하고 잔액을 바꾸지 않는다")
    void givenUnhandledEventType_whenWebhook_thenRecordedButNoTicketChange() throws Exception {
        mockMvc.perform(post("/api/v1/iap/webhook")
                        .header(HttpHeaders.AUTHORIZATION, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("EVT-7", "BILLING_ISSUE", "TXN-7")))
                .andExpect(status().isOk());

        assertThat(ticketService.getWallet(USER_ID).balance()).isZero();
        assertThat(purchaseCount()).isZero();
        // 처리하지 않아도 이벤트 자체는 남겨 재수신 시 멱등이 성립한다.
        assertThat(webhookEventCount()).isEqualTo(1L);
    }

    private long unrevokedOf(String txn) {
        return jdbcTemplate.queryForObject(
                "SELECT unrevoked_ticket_amount FROM iap_purchase WHERE store_transaction_id = ?", Long.class, txn);
    }

    private long purchaseCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM iap_purchase", Long.class);
    }

    private long webhookEventCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM iap_webhook_event", Long.class);
    }
}
