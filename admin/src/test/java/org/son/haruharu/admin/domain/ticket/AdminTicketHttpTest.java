package org.son.haruharu.admin.domain.ticket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.admin.security.AdminJwtProvider;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 포트와 어드민 JWT 로 운영진 티켓 지급의 역할 제한과 응답을 확인한다(#492). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:admin492http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminTicketHttpTest {
    @Autowired AdminJwtProvider tokens;
    @Autowired TicketService ticketService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JsonMapper json;
    @LocalServerPort int port;

    private HttpResponse<String> request(String method, String path, String body, AdminRole role) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (role != null) {
            request.header("Authorization", "Bearer " + tokens.generateAccessToken(9L, role));
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    @DisplayName("SUPER 토큰만 티켓을 지급할 수 있고, 지급 결과가 유저 상세에 반영된다")
    void givenRealHttp_whenGrant_thenOnlySuperSucceeds() throws Exception {
        jdbcTemplate.update("INSERT INTO users (status, created_at, updated_at) VALUES ('ACTIVE', now(), now())");
        Long userId = jdbcTemplate.queryForObject("SELECT max(id) FROM users", Long.class);
        ticketService.createWallet(userId);
        String path = "/api/v1/admin/users/" + userId + "/tickets";
        String body = "{\"amount\":2,\"memo\":\"결제 오류 보상\"}";

        assertThat(request("POST", path, body, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", path, body, AdminRole.CS).statusCode()).isEqualTo(403);
        assertThat(request("POST", path, body, AdminRole.OPERATOR).statusCode()).isEqualTo(403);

        HttpResponse<String> granted = request("POST", path, body, AdminRole.SUPER);
        assertThat(granted.statusCode()).isEqualTo(200);
        assertThat(json.readTree(granted.body()).path("data").path("balance").asLong()).isEqualTo(2);

        // 조회는 CS 도 가능하다. 잔액은 단일 값으로 내려간다.
        HttpResponse<String> detail = request("GET", "/api/v1/admin/users/" + userId, null, AdminRole.CS);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detail.body()).path("data").path("ticket").path("balance").asLong()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ref_id FROM ticket_ledger WHERE user_id = ? AND reason = 'ADMIN_GRANT'", String.class, userId))
                .isEqualTo("9");
    }
}
