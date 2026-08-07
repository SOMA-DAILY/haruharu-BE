package org.son.haruharu.domain.coin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.implement.CoinWalletReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 지갑 생성이 멱등하고, soft-delete된 지갑이 남은 계정에서도 유니크 충돌 없이 동작하는지 검증한다(#298).
 *
 * <p>coin_wallet.user_id 유니크 제약은 deleted_at과 무관하게 걸리는데 @SoftDelete가 붙은 일반 조회는
 * 죽은 행을 보지 못한다. 존재 확인을 일반 조회로 하면 "지갑 없음"으로 오판해 INSERT를 시도하고
 * 제약 위반이 난다 — 탈퇴로 지갑이 soft-delete된 계정이 실제로 이 상태다(dev DB에서 6건 확인).
 */
@SpringBootTest
@ActiveProfiles("test")
class CoinWalletCreationIntegrationTest {

    @Autowired CoinService coinService;
    @Autowired CoinWalletReader coinWalletReader;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM coin_ledger");
        jdbcTemplate.update("DELETE FROM coin_wallet");
    }

    @Test
    @DisplayName("지갑이 없으면 0 잔액으로 새로 만든다")
    void givenNoWallet_whenCreate_thenCreatesWithZeroBalance() {
        Long userId = 910L;

        coinService.createWallet(userId);

        assertThat(coinWalletReader.getByUserId(userId).totalBalance()).isZero();
    }

    @Test
    @DisplayName("이미 지갑이 있으면 다시 만들지 않는다(멱등)")
    void givenExistingWallet_whenCreateAgain_thenKeepsBalance() {
        Long userId = 911L;
        coinService.createWallet(userId);
        jdbcTemplate.update("UPDATE coin_wallet SET reward_balance = 50 WHERE user_id = ?", userId);

        coinService.createWallet(userId);

        assertThat(coinWalletReader.getByUserId(userId).rewardBalance())
                .as("기존 잔액이 0으로 덮이면 안 된다")
                .isEqualTo(50L);
    }

    @Test
    @DisplayName("soft-delete된 지갑이 남아 있어도 유니크 충돌 없이 통과한다")
    void givenSoftDeletedWallet_whenCreate_thenNoUniqueViolation() {
        Long userId = 912L;
        coinService.createWallet(userId);
        // 탈퇴 시 지갑 soft-delete와 같은 상태를 만든다(행은 남고 deleted_at만 채워진다).
        jdbcTemplate.update("UPDATE coin_wallet SET deleted_at = now() WHERE user_id = ?", userId);

        assertThatCode(() -> coinService.createWallet(userId)).doesNotThrowAnyException();

        // 죽은 행이 유니크 슬롯을 점유하므로 새로 만들지 않는다 — 활성 지갑은 여전히 없다.
        assertThat(coinWalletReader.findByUserId(userId)).isEmpty();
        assertThat(walletRowCount(userId)).as("행이 중복 생성되지 않아야 한다").isEqualTo(1L);
    }

    private long walletRowCount(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM coin_wallet WHERE user_id = ?", Long.class, userId);
    }
}
