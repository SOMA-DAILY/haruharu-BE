package org.son.haruharu.domain.coin;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinSpendCommand;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
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
class CoinLedgerApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired CoinService coinService;
    @Autowired JdbcTemplate jdbcTemplate;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM coin_ledger");
        jdbcTemplate.update("DELETE FROM coin_wallet");
    }

    @Test
    @DisplayName("이용내역 조회 시 각 거래의 거래 후 잔액(balance_after)이 함께 반환된다")
    void givenTransactions_whenGetLedger_thenEachItemHasBalanceAfter() throws Exception {
        Long userId = 500L;
        // 유료 100 적립 → 잔액 100
        coinService.credit(new CoinCreditCommand(
                userId, CoinBucket.PAID, 100, CoinLedgerReason.PURCHASE, "IAP", "r1"));
        // 리워드 50 적립 → 잔액 150
        coinService.credit(CoinCreditCommand.matchRefund(userId, 50, "MATCH", "r2"));
        // 80 소모(리워드 먼저): 리워드 -50 → 잔액 100, 유료 -30 → 잔액 70
        coinService.spend(new CoinSpendCommand(userId, 80, CoinLedgerReason.MATCH_ENTER, "MATCH", "r3"));

        mockMvc.perform(get("/api/v1/coin/ledger").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                // 각 거래의 balance_after 스냅샷 (순서 무관하게 값 검증)
                .andExpect(jsonPath("$.data.items[?(@.ref_id == 'r1')].balance_after").value(hasItem(100)))
                .andExpect(jsonPath("$.data.items[?(@.ref_id == 'r2')].balance_after").value(hasItem(150)))
                .andExpect(jsonPath("$.data.items[?(@.delta == -50)].balance_after").value(hasItem(100)))
                .andExpect(jsonPath("$.data.items[?(@.delta == -30)].balance_after").value(hasItem(70)));
    }
}
