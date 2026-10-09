package org.son.haruharu.domain.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.implement.TicketWalletReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 지갑 생성이 멱등하고, soft-delete된 지갑이 남은 계정에서도 유니크 충돌 없이 동작하는지 검증한다(#298).
 *
 * <p>ticket_wallet.user_id 유니크 제약은 deleted_at과 무관하게 걸리는데 @SoftDelete가 붙은 일반 조회는
 * 죽은 행을 보지 못한다. 존재 확인을 일반 조회로 하면 "지갑 없음"으로 오판해 INSERT를 시도하고
 * 제약 위반이 난다 — 탈퇴로 지갑이 soft-delete된 계정이 실제로 이 상태다(dev DB에서 6건 확인).
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketWalletCreationIntegrationTest {

    @Autowired TicketService ticketService;
    @Autowired TicketWalletReader ticketWalletReader;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM ticket_ledger");
        jdbcTemplate.update("DELETE FROM ticket_wallet");
    }

    @Test
    @DisplayName("지갑이 없으면 0 잔액으로 새로 만든다")
    void givenNoWallet_whenCreate_thenCreatesWithZeroBalance() {
        Long userId = 910L;

        ticketService.createWallet(userId);

        assertThat(ticketWalletReader.getByUserId(userId).balance()).isZero();
    }

    @Test
    @DisplayName("이미 지갑이 있으면 다시 만들지 않는다(멱등)")
    void givenExistingWallet_whenCreateAgain_thenKeepsBalance() {
        Long userId = 911L;
        ticketService.createWallet(userId);
        jdbcTemplate.update("UPDATE ticket_wallet SET balance = 5 WHERE user_id = ?", userId);

        ticketService.createWallet(userId);

        assertThat(ticketWalletReader.getByUserId(userId).balance())
                .as("기존 잔액이 0으로 덮이면 안 된다")
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("soft-delete된 지갑이 남아 있어도 유니크 충돌 없이 통과한다")
    void givenSoftDeletedWallet_whenCreate_thenNoUniqueViolation() {
        Long userId = 912L;
        ticketService.createWallet(userId);
        // 탈퇴 시 지갑 soft-delete와 같은 상태를 만든다(행은 남고 deleted_at만 채워진다).
        jdbcTemplate.update("UPDATE ticket_wallet SET deleted_at = now() WHERE user_id = ?", userId);

        assertThatCode(() -> ticketService.createWallet(userId)).doesNotThrowAnyException();

        // 죽은 행이 유니크 슬롯을 점유하므로 새로 만들지 않는다 — 활성 지갑은 여전히 없다.
        assertThat(ticketWalletReader.findByUserId(userId)).isEmpty();
        assertThat(walletRowCount(userId)).as("행이 중복 생성되지 않아야 한다").isEqualTo(1L);
    }

    private long walletRowCount(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_wallet WHERE user_id = ?", Long.class, userId);
    }
}
