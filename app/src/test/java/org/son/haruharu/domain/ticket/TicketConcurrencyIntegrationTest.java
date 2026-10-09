package org.son.haruharu.domain.ticket;

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
import org.son.haruharu.domain.payment.business.PaymentService;
import org.son.haruharu.domain.payment.business.command.IapWebhookCommand;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.business.command.TicketRevokeCommand;
import org.son.haruharu.domain.ticket.business.command.TicketSpendCommand;
import org.son.haruharu.domain.ticket.implement.TicketWalletReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지갑 동시 갱신이 lost update 없이 직렬화되는지 검증한다(#298).
 *
 * <p>비관적 락(SELECT ... FOR UPDATE) 도입 전에는 두 트랜잭션이 같은 잔액을 읽고 각자 계산한 값을 덮어써서
 * 한쪽 갱신이 사라졌다. 원장(ticket_ledger)은 append-only라 두 건 다 남으므로 원장 합계와 지갑 잔액이
 * 어긋나는 형태로 드러난다 — 그래서 최종 잔액뿐 아니라 원장 건수까지 함께 확인한다.
 *
 * <p>실제 진입 경로가 겹친다: 적립은 RevenueCat webhook·운영진 지급에서, 회수는 환불 webhook에서 호출된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TicketConcurrencyIntegrationTest {

    // 행 락 대기와 대기 후 재조회는 PostgreSQL 동작이라 H2 가 아닌 컨테이너로 확인한다(#492).
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private static final Long USER_ID = 900L;

    @Autowired TicketService ticketService;
    @Autowired PaymentService paymentService;
    @Autowired TicketWalletReader ticketWalletReader;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM ticket_ledger");
        jdbcTemplate.update("DELETE FROM ticket_wallet");
        ticketService.createWallet(USER_ID);
    }

    @Test
    @DisplayName("같은 유저에게 동시 적립 20건이 들어와도 모두 반영된다")
    void givenConcurrentCredits_whenAllComplete_thenNoLostUpdate() throws Exception {
        int threads = 20;
        long amountEach = 10L;

        List<Throwable> failures = runConcurrently(threads, i -> ticketService.credit(
                TicketCreditCommand.purchase(USER_ID, amountEach, "TEST", "ref-" + i)));

        assertThat(failures).isEmpty();
        assertThat(ticketWalletReader.getByUserId(USER_ID).balance())
                .as("적립 %d건이 모두 반영되어야 한다", threads)
                .isEqualTo(threads * amountEach);
        assertThat(ledgerCount()).isEqualTo(threads);
    }

    @Test
    @DisplayName("동시 적립과 사용이 섞여도 최종 잔액이 원장 합계와 일치한다")
    void givenConcurrentCreditAndSpend_whenAllComplete_thenBalanceMatchesLedger() throws Exception {
        // 사용이 잔액 부족으로 실패하지 않도록 충분히 채워둔다.
        ticketService.credit(TicketCreditCommand.purchase(USER_ID, 1000L, "TEST", "seed"));

        int pairs = 10;
        List<Throwable> failures = runConcurrently(pairs * 2, i -> {
            if (i % 2 == 0) {
                ticketService.credit(TicketCreditCommand.purchase(USER_ID, 10L, "TEST", "c-" + i));
            } else {
                ticketService.spend(new TicketSpendCommand(USER_ID, 5L, "TEST", "s-" + i));
            }
        });

        assertThat(failures).isEmpty();
        long expected = 1000L + (pairs * 10L) - (pairs * 5L);
        assertThat(ticketWalletReader.getByUserId(USER_ID).balance()).isEqualTo(expected);
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

    @Test
    @DisplayName("같은 유저의 환불 회수가 동시에 몰려도 잔액은 0 아래로 내려가지 않고 회수 합계는 원래 잔액과 같다")
    void givenConcurrentRevokes_whenAllComplete_thenBalanceStopsAtZero() throws Exception {
        ticketService.credit(TicketCreditCommand.purchase(USER_ID, 3L, "TEST", "seed"));

        int threads = 10;
        java.util.concurrent.atomic.AtomicLong revokedSum = new java.util.concurrent.atomic.AtomicLong();
        List<Throwable> failures = runConcurrently(threads, i -> revokedSum.addAndGet(
                ticketService.revoke(new TicketRevokeCommand(USER_ID, 1L, "TEST", "r-" + i))));

        assertThat(failures).isEmpty();
        assertThat(ticketWalletReader.getByUserId(USER_ID).balance()).isZero();
        assertThat(revokedSum.get()).as("실제 회수 합계는 갖고 있던 3장을 넘지 않는다").isEqualTo(3L);
        assertThat(ledgerDeltaSum()).as("원장 합계는 지갑 잔액(0)과 일치해야 한다").isZero();
    }

    @Test
    @DisplayName("같은 거래의 환불 이벤트가 서로 다른 id 로 동시에 와도 한 번만 회수한다")
    void givenConcurrentCancellationsOfSamePurchase_whenAllComplete_thenRevokedOnce() throws Exception {
        jdbcTemplate.update("DELETE FROM iap_webhook_event");
        jdbcTemplate.update("DELETE FROM iap_purchase");
        jdbcTemplate.update("DELETE FROM iap_product");
        jdbcTemplate.update("""
                INSERT INTO iap_product (created_at, updated_at, store_product_id, store, ticket_amount, price, currency, active)
                VALUES (now(), now(), 'ticket_1', 'APP_STORE', 1, 7900, 'KRW', true)""");
        // 이 구매 1장 + 다른 구매로 쌓인 2장. 이중 회수가 나면 다른 구매분까지 깎인다.
        paymentService.handleWebhook(webhook("P-1", "NON_RENEWING_PURCHASE", "TXN-C"));
        ticketService.credit(TicketCreditCommand.purchase(USER_ID, 2L, "TEST", "other"));

        List<Throwable> failures = runConcurrently(8, i ->
                paymentService.handleWebhook(webhook("C-" + i, "CANCELLATION", "TXN-C")));

        assertThat(failures).isEmpty();
        assertThat(ticketWalletReader.getByUserId(USER_ID).balance()).as("이 구매분 1장만 회수된다").isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_ledger WHERE reason = 'REFUND_REVOKE'", Long.class)).isEqualTo(1L);
    }

    private IapWebhookCommand webhook(String eventId, String type, String txn) {
        return new IapWebhookCommand(eventId, type, String.valueOf(USER_ID), "ticket_1", IapStore.APP_STORE,
                txn, java.time.LocalDateTime.now(), "PRODUCTION", "{}");
    }

    private long ledgerCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ticket_ledger WHERE user_id = ?", Long.class, USER_ID);
    }

    private long ledgerDeltaSum() {
        return jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(delta), 0) FROM ticket_ledger WHERE user_id = ?", Long.class, USER_ID);
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int index) throws Exception;
    }
}
