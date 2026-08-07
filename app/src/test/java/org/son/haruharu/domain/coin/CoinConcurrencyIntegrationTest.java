package org.son.haruharu.domain.coin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.coin.business.command.CoinCreditCommand;
import org.son.haruharu.domain.coin.business.command.CoinSpendCommand;
import org.son.haruharu.domain.coin.implement.CoinBucket;
import org.son.haruharu.domain.coin.implement.CoinLedgerReason;
import org.son.haruharu.domain.coin.implement.CoinWalletReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지갑 동시 갱신이 lost update 없이 직렬화되는지 검증한다(#298).
 *
 * <p>비관적 락(SELECT ... FOR UPDATE) 도입 전에는 두 트랜잭션이 같은 잔액을 읽고 각자 계산한 값을 덮어써서
 * 한쪽 갱신이 사라졌다. 원장(coin_ledger)은 append-only라 두 건 다 남으므로 원장 합계와 지갑 잔액이
 * 어긋나는 형태로 드러난다 — 그래서 최종 잔액뿐 아니라 원장 건수까지 함께 확인한다.
 *
 * <p>실제 진입 경로가 겹친다: credit은 유저 요청·22:00 배치·RevenueCat webhook 세 갈래에서 호출된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoinConcurrencyIntegrationTest {

    private static final Long USER_ID = 900L;

    @Autowired CoinService coinService;
    @Autowired CoinWalletReader coinWalletReader;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM coin_ledger");
        jdbcTemplate.update("DELETE FROM coin_wallet");
        coinService.createWallet(USER_ID);
    }

    @Test
    @DisplayName("같은 유저에게 동시 적립 20건이 들어와도 모두 반영된다")
    void givenConcurrentCredits_whenAllComplete_thenNoLostUpdate() throws Exception {
        int threads = 20;
        long amountEach = 10L;

        List<Throwable> failures = runConcurrently(threads, i -> coinService.credit(
                new CoinCreditCommand(USER_ID, CoinBucket.REWARD, amountEach,
                        CoinLedgerReason.MATCH_REFUND, "TEST", "ref-" + i)));

        assertThat(failures).isEmpty();
        assertThat(coinWalletReader.getByUserId(USER_ID).totalBalance())
                .as("적립 %d건이 모두 반영되어야 한다", threads)
                .isEqualTo(threads * amountEach);
        assertThat(ledgerCount()).isEqualTo(threads);
    }

    @Test
    @DisplayName("동시 적립과 소모가 섞여도 최종 잔액이 원장 합계와 일치한다")
    void givenConcurrentCreditAndSpend_whenAllComplete_thenBalanceMatchesLedger() throws Exception {
        // 소모가 잔액 부족으로 실패하지 않도록 충분히 채워둔다.
        coinService.credit(new CoinCreditCommand(USER_ID, CoinBucket.REWARD, 1000L,
                CoinLedgerReason.MATCH_REFUND, "TEST", "seed"));

        int pairs = 10;
        List<Throwable> failures = runConcurrently(pairs * 2, i -> {
            if (i % 2 == 0) {
                coinService.credit(new CoinCreditCommand(USER_ID, CoinBucket.REWARD, 10L,
                        CoinLedgerReason.MATCH_REFUND, "TEST", "c-" + i));
            } else {
                coinService.spend(new CoinSpendCommand(USER_ID, 5L,
                        CoinLedgerReason.MATCH_ENTER, "TEST", "s-" + i));
            }
        });

        assertThat(failures).isEmpty();
        long expected = 1000L + (pairs * 10L) - (pairs * 5L);
        assertThat(coinWalletReader.getByUserId(USER_ID).totalBalance()).isEqualTo(expected);
        assertThat(ledgerDeltaSum())
                .as("원장 delta 합계는 지갑 잔액과 일치해야 한다")
                .isEqualTo(expected);
    }

    /** 모든 작업을 동시에 출발시키고(래치), 끝날 때까지 기다린 뒤 발생한 예외를 모아 반환한다. */
    private List<Throwable> runConcurrently(int count, ThrowingIntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger index = new AtomicInteger();

        try {
            for (int i = 0; i < count; i++) {
                pool.submit(() -> {
                    int myIndex = index.getAndIncrement();
                    try {
                        start.await();
                        task.accept(myIndex);
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("모든 작업이 30초 내에 끝나야 한다").isTrue();
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    private long ledgerCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM coin_ledger WHERE user_id = ?", Long.class, USER_ID);
    }

    private long ledgerDeltaSum() {
        return jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(delta), 0) FROM coin_ledger WHERE user_id = ?", Long.class, USER_ID);
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int index) throws Exception;
    }
}
