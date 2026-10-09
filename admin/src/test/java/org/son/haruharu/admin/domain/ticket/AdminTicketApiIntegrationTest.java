package org.son.haruharu.admin.domain.ticket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.ticket.implement.TicketWallet;
import org.son.haruharu.domain.ticket.storage.TicketWalletEntity;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminTicketApiIntegrationTest {

    private static final Long ADMIN_ID = 7L;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @PersistenceContext
    EntityManager em;

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(ADMIN_ID, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Long persistUser() {
        UserEntity entity = UserEntity.from(new User(null, UserStatus.ACTIVE, null));
        em.persist(entity);
        return entity.toDomain().id();
    }

    private Long persistUserWithWallet(long balance) {
        Long userId = persistUser();
        em.persist(TicketWalletEntity.from(new TicketWallet(null, userId, balance, null)));
        em.flush();
        return userId;
    }

    private ResultActions grant(String role, Long userId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/users/{userId}/tickets", userId)
                .with(authentication(auth(role))).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long balanceOf(Long userId) {
        em.flush();
        return jdbcTemplate.queryForObject("SELECT balance FROM ticket_wallet WHERE user_id = ?", Long.class, userId);
    }

    private long ledgerCount(Long userId) {
        em.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM ticket_ledger WHERE user_id = ?", Long.class, userId);
    }

    @Test
    @DisplayName("SUPER가 티켓을 지급하면 잔액이 늘고 원장에 지급한 어드민과 사유가 남는다")
    void super_grant_creditsAndRecordsLedger() throws Exception {
        Long userId = persistUserWithWallet(1);

        grant("SUPER", userId, "{\"amount\":2,\"memo\":\"결제 오류 보상\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user_id").value(userId))
                .andExpect(jsonPath("$.data.granted_amount").value(2))
                .andExpect(jsonPath("$.data.balance").value(3));

        assertThat(balanceOf(userId)).isEqualTo(3L);
        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                "SELECT delta, balance_after, reason, ref_type, ref_id, memo FROM ticket_ledger WHERE user_id = ?", userId);
        assertThat(ledger.get("delta")).isEqualTo(2L);
        assertThat(ledger.get("balance_after")).isEqualTo(3L);
        assertThat(ledger.get("reason")).isEqualTo("ADMIN_GRANT");
        assertThat(ledger.get("ref_type")).isEqualTo("ADMIN");
        assertThat(ledger.get("ref_id")).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(ledger.get("memo")).isEqualTo("결제 오류 보상");
    }

    @Test
    @DisplayName("OPERATOR·CS는 티켓을 지급할 수 없어 403이고 잔액은 그대로다")
    void nonSuper_grant_returns403() throws Exception {
        Long userId = persistUserWithWallet(0);

        for (String role : List.of("OPERATOR", "CS")) {
            grant(role, userId, "{\"amount\":1,\"memo\":\"보상\"}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        assertThat(balanceOf(userId)).isZero();
        assertThat(ledgerCount(userId)).isZero();
    }

    @Test
    @DisplayName("인증 없이 지급을 시도하면 401")
    void noAuth_grant_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/tickets", 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1,\"memo\":\"보상\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("수량이 0 이하이거나 상한(100)을 넘거나 사유가 비면 400이고 지급되지 않는다")
    void invalidRequest_returns400() throws Exception {
        Long userId = persistUserWithWallet(0);

        for (String body : List.of(
                "{\"amount\":0,\"memo\":\"보상\"}",
                "{\"amount\":-1,\"memo\":\"보상\"}",
                "{\"amount\":101,\"memo\":\"보상\"}",
                "{\"amount\":1,\"memo\":\" \"}",
                "{\"amount\":1}")) {
            grant("SUPER", userId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }

        assertThat(balanceOf(userId)).isZero();
        assertThat(ledgerCount(userId)).isZero();
    }

    @Test
    @DisplayName("없는 유저에게 지급하면 404")
    void unknownUser_returns404() throws Exception {
        grant("SUPER", 999_999_999L, "{\"amount\":1,\"memo\":\"보상\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ADMIN_USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("지갑이 없는 유저에게 지급하면 404이고 원장도 남지 않는다")
    void userWithoutWallet_returns404() throws Exception {
        Long userId = persistUser();
        em.flush();

        grant("SUPER", userId, "{\"amount\":1,\"memo\":\"보상\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TICKET_WALLET_NOT_FOUND"));

        assertThat(ledgerCount(userId)).isZero();
    }
}
