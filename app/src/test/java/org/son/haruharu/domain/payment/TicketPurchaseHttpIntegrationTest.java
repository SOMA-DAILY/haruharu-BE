package org.son.haruharu.domain.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.payment.implement.IapProduct;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.payment.storage.IapProductEntity;
import org.son.haruharu.domain.payment.storage.IapProductJpaRepository;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 포트로 결제 → 티켓 흐름을 확인한다(#492). 시크릿 헤더 인증, JWT 필터, snake_case 직렬화까지
 * 서블릿 컨테이너를 거친 요청·응답으로 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:ticket492http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TicketPurchaseHttpIntegrationTest {

    private static final String PRODUCT_ID = "org.son.haruharu.ticket.1";

    @LocalServerPort int port;
    @Value("${revenuecat.webhook-secret}") String secret;
    @Autowired UserJpaRepository userRepository;
    @Autowired IapProductJpaRepository productRepository;
    @Autowired TicketService ticketService;
    @Autowired JwtProvider jwtProvider;
    @Autowired JsonMapper json;

    private HttpResponse<String> webhook(HttpClient client, String authorization, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/iap/webhook"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(HttpClient client, String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String event(String eventId, String type, Long userId, String txn, String environment) {
        return """
                {"event":{"id":"%s","type":"%s","app_user_id":"%d","product_id":"%s","store":"APP_STORE",
                "transaction_id":"%s","purchased_at_ms":1750000000000,"environment":"%s"}}
                """.formatted(eventId, type, userId, PRODUCT_ID, txn, environment);
    }

    @Test
    @DisplayName("구매 webhook → 잔액·내역 조회 → 환불 webhook 까지 실제 HTTP 로 이어진다")
    void givenRealHttp_whenPurchaseThenRefund_thenTicketsFollow() throws Exception {
        Long userId = userRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        Long otherId = userRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        ticketService.createWallet(userId);
        ticketService.createWallet(otherId);
        productRepository.save(IapProductEntity.from(
                new IapProduct(null, PRODUCT_ID, IapStore.APP_STORE, 1L, 7900, "KRW", true)));
        String token = jwtProvider.generateAccessToken(userId);

        try (HttpClient client = HttpClient.newHttpClient()) {
            // 인증: 시크릿 없거나 틀리면 401, 유저 API 는 JWT 없이 401
            assertThat(webhook(client, null, event("H-0", "NON_RENEWING_PURCHASE", userId, "HT-0", "PRODUCTION")).statusCode())
                    .isEqualTo(401);
            assertThat(webhook(client, "wrong", event("H-0", "NON_RENEWING_PURCHASE", userId, "HT-0", "PRODUCTION")).statusCode())
                    .isEqualTo(401);
            assertThat(get(client, "/api/v1/ticket/wallet", null).statusCode()).isEqualTo(401);

            // 상품 목록: 보너스 필드 없이 티켓 수량만
            JsonNode product = json.readTree(get(client, "/api/v1/ticket/products", token).body())
                    .path("data").path("products").get(0);
            assertThat(product.path("store_product_id").asText()).isEqualTo(PRODUCT_ID);
            assertThat(product.path("ticket_amount").asLong()).isEqualTo(1);
            assertThat(product.has("bonus_amount")).isFalse();
            assertThat(product.has("coin_amount")).isFalse();

            // 샌드박스 결제는 200 으로 받되 적립하지 않는다
            assertThat(webhook(client, secret, event("H-1", "NON_RENEWING_PURCHASE", userId, "HT-1", "SANDBOX")).statusCode())
                    .isEqualTo(200);
            assertThat(json.readTree(get(client, "/api/v1/ticket/wallet", token).body()).path("data").path("balance").asLong())
                    .isZero();

            // 운영 결제는 적립된다. 같은 이벤트 재전송은 한 번만 반영된다.
            String purchase = event("H-2", "NON_RENEWING_PURCHASE", userId, "HT-2", "PRODUCTION");
            assertThat(webhook(client, secret, purchase).statusCode()).isEqualTo(200);
            assertThat(webhook(client, secret, purchase).statusCode()).isEqualTo(200);
            JsonNode wallet = json.readTree(get(client, "/api/v1/ticket/wallet", token).body()).path("data");
            assertThat(wallet.path("balance").asLong()).isEqualTo(1);
            assertThat(wallet.has("paid_balance")).isFalse();

            JsonNode items = json.readTree(get(client, "/api/v1/ticket/ledger", token).body()).path("data").path("items");
            assertThat(items.size()).isEqualTo(1);
            assertThat(items.get(0).path("reason").asText()).isEqualTo("PURCHASE");
            assertThat(items.get(0).path("delta").asLong()).isEqualTo(1);
            assertThat(items.get(0).path("balance_after").asLong()).isEqualTo(1);
            assertThat(items.get(0).has("memo")).isFalse();

            // 다른 유저에게는 내 내역이 보이지 않는다
            JsonNode others = json.readTree(get(client, "/api/v1/ticket/ledger", jwtProvider.generateAccessToken(otherId)).body());
            assertThat(others.path("data").path("items").size()).isZero();

            // 환불되면 회수된다
            assertThat(webhook(client, secret, event("H-3", "CANCELLATION", userId, "HT-2", "PRODUCTION")).statusCode())
                    .isEqualTo(200);
            assertThat(json.readTree(get(client, "/api/v1/ticket/wallet", token).body()).path("data").path("balance").asLong())
                    .isZero();
            JsonNode after = json.readTree(get(client, "/api/v1/ticket/ledger", token).body()).path("data").path("items");
            assertThat(after.size()).isEqualTo(2);
        }
    }
}
