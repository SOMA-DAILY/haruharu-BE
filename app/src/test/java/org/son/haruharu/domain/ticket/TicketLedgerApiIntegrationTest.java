package org.son.haruharu.domain.ticket;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.business.command.TicketSpendCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketLedgerApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TicketService ticketService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM ticket_ledger");
        jdbcTemplate.update("DELETE FROM ticket_wallet");
    }

    @Test
    @DisplayName("이용내역 조회 시 각 거래의 거래 후 잔액(balance_after)이 함께 반환된다")
    void givenTransactions_whenGetLedger_thenEachItemHasBalanceAfter() throws Exception {
        Long userId = 500L;
        // 지갑은 가입 시점에 생성된다(#298). 이 테스트는 가입 경로를 타지 않으므로 직접 만든다.
        ticketService.createWallet(userId);
        // 구매 2장 → 잔액 2
        ticketService.credit(TicketCreditCommand.purchase(userId, 2, "IAP_PURCHASE", "r1"));
        // 운영진 지급 1장 → 잔액 3. 메모는 내부 기록이라 응답에 실리지 않아야 한다.
        ticketService.credit(TicketCreditCommand.adminGrant(userId, 1, 7L, "내부 메모"));
        // 1장 사용 → 잔액 2
        ticketService.spend(new TicketSpendCommand(userId, 1, "ROOM", "r3"));

        mockMvc.perform(get("/api/v1/ticket/ledger").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                // 각 거래의 balance_after 스냅샷 (순서 무관하게 값 검증)
                .andExpect(jsonPath("$.data.items[?(@.reason == 'PURCHASE')].balance_after").value(hasItem(2)))
                .andExpect(jsonPath("$.data.items[?(@.reason == 'ADMIN_GRANT')].balance_after").value(hasItem(3)))
                .andExpect(jsonPath("$.data.items[?(@.reason == 'USE')].delta").value(hasItem(-1)))
                .andExpect(jsonPath("$.data.items[?(@.reason == 'USE')].balance_after").value(hasItem(2)))
                // 운영 지급 항목은 지급한 어드민 id(ref)와 메모를 유저에게 내려주지 않는다.
                .andExpect(jsonPath("$.data.items[?(@.reason == 'ADMIN_GRANT')].ref_id").value(hasItem((Object) null)))
                .andExpect(jsonPath("$.data.items[?(@.reason == 'PURCHASE')].ref_id").value(hasItem("r1")))
                .andExpect(jsonPath("$.data.items[0].memo").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].bucket").doesNotExist());
    }

    @Test
    @DisplayName("잔액 조회는 단일 잔액만 내려준다")
    void givenTickets_whenGetWallet_thenSingleBalance() throws Exception {
        Long userId = 503L;
        ticketService.createWallet(userId);
        ticketService.credit(TicketCreditCommand.purchase(userId, 2, "IAP_PURCHASE", "r1"));

        mockMvc.perform(get("/api/v1/ticket/wallet").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(2))
                .andExpect(jsonPath("$.data.paid_balance").doesNotExist());
    }

    @Test
    @DisplayName("숫자가 아닌 커서로 조회하면 400을 반환한다 — 서버 오류(500)가 아니다")
    void givenNonNumericCursor_whenGetLedger_thenBadRequest() throws Exception {
        Long userId = 501L;
        ticketService.createWallet(userId);

        mockMvc.perform(get("/api/v1/ticket/ledger").param("cursor", "abc")
                        .with(authentication(auth(userId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 커서 id로 조회하면 첫 페이지를 반환한다")
    void givenUnknownCursor_whenGetLedger_thenReturnsFirstPage() throws Exception {
        Long userId = 502L;
        ticketService.createWallet(userId);
        ticketService.credit(TicketCreditCommand.purchase(userId, 1, "IAP_PURCHASE", "r1"));

        mockMvc.perform(get("/api/v1/ticket/ledger").param("cursor", "99999999")
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }
}
