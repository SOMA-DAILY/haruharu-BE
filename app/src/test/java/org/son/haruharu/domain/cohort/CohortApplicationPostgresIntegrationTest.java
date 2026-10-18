package org.son.haruharu.domain.cohort;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.business.CohortApplicationScreeningService;
import org.son.haruharu.domain.cohort.business.CohortApplicationService;
import org.son.haruharu.domain.cohort.business.CohortService;
import org.son.haruharu.domain.cohort.business.command.ApplyCohortCommand;
import org.son.haruharu.domain.cohort.business.dto.CohortApplicationResponse;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationInProgressException;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationNotCancellableException;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;
import org.son.haruharu.domain.cohort.implement.TrackQuestionType;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketCreditCommand;
import org.son.haruharu.domain.ticket.implement.exception.InsufficientTicketException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #511 실제 PostgreSQL 검증. Hibernate 가 만든 스키마 위에 510·511 up 을 덮어 verify 를 통과하는지 보고,
 * 행 잠금·조건부 전이·부분 유니크가 실제로 동시 요청을 한 건으로 줄이는지 확인한다(H2 로는 잠금 대기를 재현할 수 없다).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CohortApplicationPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private static final ApplyCohortCommand FORM = new ApplyCohortCommand("한국대", "컴퓨터공학과", "01012345678",
            Map.of("grade", "3학년"));

    @Autowired CohortApplicationService applicationService;
    @Autowired CohortApplicationScreeningService screeningService;
    @Autowired UserIdentityWriter userIdentityWriter;
    @Autowired CohortService cohortService;
    @Autowired TicketService ticketService;
    @Autowired RecruitTrackWriter trackWriter;
    @Autowired CohortWriter cohortWriter;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired javax.sql.DataSource dataSource;

    private Long cohortId;

    @BeforeEach
    void applyMigrationsOverHibernateSchema() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE cohort_applications, cohorts, recruit_tracks, ticket_ledger, ticket_wallet,"
                + " user_profiles, user_identities RESTART IDENTITY CASCADE");
        for (String script : List.of("510-recruit-cohort-up", "510-recruit-cohort-verify", "511-cohort-application-up",
                "511-cohort-application-verify")) {
            jdbcTemplate.execute(sql(script));
        }
        RecruitTrack track = trackWriter.save(RecruitTrack.create("seoul-student", "서울 대학생", "서울",
                RecruitAudience.STUDENT, 20, 29, List.of(new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE,
                        true, List.of("1학년", "3학년"))), 1));
        LocalDateTime now = LocalDateTime.now();
        cohortId = cohortWriter.save(new Cohort(null, track.id(), 6, CohortStatus.OPEN, now.minusDays(1),
                now.plusDays(3), now.plusDays(4), "10월 마지막 주", 6, 6, null, "admin:1", null, null)).id();
    }

    private static String sql(String name) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing migration scripts");
        return Files.readString(root.resolve("scripts/sql/" + name + ".sql"));
    }

    /** 실패할 수 있는 스크립트는 풀 밖 연결로 돌린다. BEGIN 뒤 실패하면 연결이 중단된 트랜잭션으로 남기 때문이다. */
    private static void executeOutsidePool(String script) throws Exception {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); var statement = c.createStatement()) {
            statement.execute(script);
        }
    }

    private void wallet(Long userId, long tickets) {
        ticketService.createWallet(userId);
        if (tickets > 0) ticketService.credit(TicketCreditCommand.adminGrant(userId, tickets, 1L, "테스트"));
    }

    private long balance(Long userId) {
        return ticketService.getWallet(userId).balance();
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    /** 같은 작업을 동시에 n 번 돌려 성공 횟수를 센다. expected 예외는 실패로 센다. */
    private long runConcurrently(int n, Callable<?> task, Class<? extends Exception> expected) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return true;
                    } catch (Exception e) {
                        if (expected.isInstance(e)) return false;
                        throw e;
                    }
                }));
            }
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> result : results) if (result.get()) succeeded++;
            return succeeded;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Hibernate 스키마 위에 up 을 덮어도 verify 를 통과하고, Hibernate 의 enum CHECK 는 ck_* 하나로 정리된다")
    void givenHibernateSchema_whenUp_thenVerifiesAndOneCheckPerEnum() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT DISTINCT att.attname || ':' || con.conname FROM pg_constraint con
                JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
                WHERE con.contype = 'c' AND con.conrelid = 'cohort_applications'::regclass
                  AND att.attname IN ('status', 'friend_preference', 'close_reason')""", String.class))
                .containsExactlyInAnyOrder("status:ck_cohort_applications_status",
                        "status:ck_cohort_applications_closed", "close_reason:ck_cohort_applications_close_reason",
                        "friend_preference:ck_cohort_applications_friend_preference");
    }

    @Test
    @DisplayName("같은 유저가 동시에 두 번 신청하면 한 건만 남고 참가권도 한 장만 빠진다")
    void givenSameUser_whenApplyConcurrently_thenOnlyOne() throws Exception {
        wallet(900L, 2);

        long succeeded = runConcurrently(2, () -> applicationService.apply(900L, cohortId, FORM),
                CohortApplicationInProgressException.class);

        assertThat(succeeded).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM cohort_applications WHERE user_id = 900")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ticket_ledger WHERE user_id = 900 AND reason = 'USE'")).isEqualTo(1);
        assertThat(balance(900L)).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 유저 여러 명이 동시에 신청해도(기수 공유 잠금) 모두 신청되고 각자 한 장씩 빠진다")
    void givenManyUsers_whenApplyConcurrently_thenAllSucceed() throws Exception {
        for (long userId = 901; userId <= 905; userId++) wallet(userId, 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<CohortApplicationResponse>> results = new ArrayList<>();
            for (long userId = 901; userId <= 905; userId++) {
                long id = userId;
                results.add(pool.submit(() -> {
                    start.await();
                    return applicationService.apply(id, cohortId, FORM);
                }));
            }
            start.countDown();
            for (Future<CohortApplicationResponse> result : results) result.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(count("SELECT count(*) FROM cohort_applications WHERE cohort_id = ?", cohortId)).isEqualTo(5);
        assertThat(count("SELECT coalesce(sum(balance), 0) FROM ticket_wallet WHERE user_id BETWEEN 901 AND 905"))
                .isZero();
    }

    @Test
    @DisplayName("참가권이 0 이면 거절되고 신청·원장 기록이 남지 않는다")
    void givenZeroBalance_whenApply_thenNothingRecorded() {
        wallet(910L, 0);

        assertThatThrownBy(() -> applicationService.apply(910L, cohortId, FORM))
                .isInstanceOf(InsufficientTicketException.class);

        assertThat(count("SELECT count(*) FROM cohort_applications WHERE user_id = 910")).isZero();
        assertThat(count("SELECT count(*) FROM ticket_ledger WHERE user_id = 910 AND reason <> 'ADMIN_GRANT'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM user_profiles WHERE user_id = 910")).isZero();
    }

    @Test
    @DisplayName("같은 신청을 동시에 두 번 취소하면 한 번만 취소되고 참가권도 한 번만 돌아온다")
    void givenApplication_whenCancelConcurrently_thenRefundOnce() throws Exception {
        wallet(920L, 1);
        Long applicationId = applicationService.apply(920L, cohortId, FORM).applicationId();

        long succeeded = runConcurrently(2, () -> applicationService.cancel(920L, applicationId),
                CohortApplicationNotCancellableException.class);

        assertThat(succeeded).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ticket_ledger WHERE reason = 'USE_REFUND' AND ref_id = ?",
                applicationId.toString())).isEqualTo(1);
        assertThat(balance(920L)).isEqualTo(1);
    }

    @Test
    @DisplayName("기수 취소와 본인 취소가 겹쳐도 신청은 한 번만 닫히고 참가권은 한 번만 돌아온다")
    void givenApplication_whenCohortCancelRacesUserCancel_thenRefundOnce() throws Exception {
        wallet(930L, 1);
        Long applicationId = applicationService.apply(930L, cohortId, FORM).applicationId();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> cohortCancel = pool.submit(() -> {
                start.await();
                return cohortService.cancel(cohortId);
            });
            Future<?> userCancel = pool.submit(() -> {
                start.await();
                try {
                    return applicationService.cancel(930L, applicationId);
                } catch (CohortApplicationNotCancellableException e) {
                    return null;
                }
            });
            start.countDown();
            cohortCancel.get();
            userCancel.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM cohort_applications WHERE id = ?", String.class,
                applicationId)).isIn("CANCELLED", "NOT_ASSIGNED");
        assertThat(count("SELECT count(*) FROM ticket_ledger WHERE reason = 'USE_REFUND' AND ref_id = ?",
                applicationId.toString())).isEqualTo(1);
        assertThat(balance(930L)).isEqualTo(1);
    }

    @Test
    @DisplayName("기수를 취소하면 배정 전 신청이 모두 미배정으로 닫혀 반환되고, 다음 신청은 우선순위 1 을 받는다")
    void givenApplications_whenCohortCancelled_thenAllRefundedAndNextPrioritized() {
        wallet(940L, 2);
        wallet(941L, 1);
        applicationService.apply(940L, cohortId, FORM);
        applicationService.apply(941L, cohortId, FORM);

        assertThat(cohortService.cancel(cohortId).status()).isEqualTo(CohortStatus.CANCELLED);

        assertThat(jdbcTemplate.queryForList("SELECT status || ':' || close_reason FROM cohort_applications"
                + " WHERE cohort_id = ?", String.class, cohortId))
                .containsOnly("NOT_ASSIGNED:COHORT_CANCELLED").hasSize(2);
        assertThat(balance(940L)).isEqualTo(2);
        assertThat(balance(941L)).isEqualTo(1);

        RecruitTrack next = trackWriter.save(RecruitTrack.create("gwangju-student", "광주 대학생", "광주",
                RecruitAudience.STUDENT, 20, 29, List.of(), 2));
        LocalDateTime now = LocalDateTime.now();
        Long nextCohort = cohortWriter.save(new Cohort(null, next.id(), 1, CohortStatus.OPEN, now.minusDays(1),
                now.plusDays(3), now.plusDays(4), "11월 첫 주", 6, 6, null, "admin:1", null, null)).id();
        applicationService.apply(940L, nextCohort, new ApplyCohortCommand("한국대", "컴퓨터공학과", "01012345678", null));
        assertThat(count("SELECT priority FROM cohort_applications WHERE user_id = 940 AND cohort_id = ?", nextCohort))
                .isEqualTo(1);
    }

    /** 이벤트 없이 PASS 결과만 남긴다 — 커밋 후 리스너가 아직 안 돌았거나 실패한 상태. */
    private void passWithoutEvent(Long userId, String gender) {
        userIdentityWriter.save(new UserIdentity(null, userId, "ci-" + userId, "di-" + userId, "01099998888",
                LocalDate.of(LocalDate.now().getYear() - 23, 1, 1), gender, "홍길동", "SKT", false,
                LocalDateTime.now()));
    }

    @Test
    @DisplayName("PASS 판정을 커밋 후 경로와 조회 경로(screenPending)가 동시에 여러 번 돌려도 전이는 정확히 한 번이다(#512)")
    void givenPass_whenScreenConcurrently_thenTransitionsOnce() throws Exception {
        wallet(950L, 1);
        Long applicationId = applicationService.apply(950L, cohortId, FORM).applicationId();
        passWithoutEvent(950L, "MALE");
        AtomicInteger transitions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                boolean viaListener = i % 2 == 0;
                results.add(pool.submit(() -> {
                    start.await();
                    boolean changed = viaListener ? screeningService.screenAfterIdentityVerified(950L)
                            : screeningService.screenPending(950L);
                    if (changed) transitions.incrementAndGet();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) result.get();
        } finally {
            pool.shutdownNow();
        }

        // 프로필이 없어 신청 성별은 PASS 성별로 채워진다
        assertThat(jdbcTemplate.queryForMap("SELECT status, declared_gender, gender FROM cohort_applications"
                + " WHERE id = ?", applicationId)).containsEntry("status", "QUEUED")
                .containsEntry("declared_gender", "MALE").containsEntry("gender", "MALE");
        assertThat(transitions.get()).isEqualTo(1);
        assertThat(screeningService.screenAfterIdentityVerified(950L)).isFalse();
        assertThat(applicationService.getMine(950L).application().status()).isEqualTo(
                CohortApplicationStatus.QUEUED);
        assertThat(balance(950L)).isZero();
    }

    @Test
    @DisplayName("PASS 성별을 해석할 수 없으면 확인 필요(UNKNOWN_GENDER)로 DB 제약을 통과해 남고, PASS 성별·생년 스냅샷이 남으며 참가권은 그대로다(#512)")
    void givenOtherGender_whenScreen_thenNeedsReviewPersisted() {
        wallet(970L, 1);
        Long applicationId = applicationService.apply(970L, cohortId, FORM).applicationId();
        passWithoutEvent(970L, "OTHER");

        assertThat(screeningService.screenAfterIdentityVerified(970L)).isTrue();

        assertThat(jdbcTemplate.queryForMap("SELECT status, review_reason, gender, queued_at FROM cohort_applications"
                + " WHERE id = ?", applicationId)).containsEntry("status", "NEEDS_REVIEW")
                .containsEntry("review_reason", "UNKNOWN_GENDER").containsEntry("gender", "OTHER")
                .containsEntry("queued_at", null);
        assertThat(jdbcTemplate.queryForObject("SELECT birthday::text FROM cohort_applications WHERE id = ?",
                String.class, applicationId)).isEqualTo(LocalDate.of(LocalDate.now().getYear() - 23, 1, 1).toString());
        assertThat(balance(970L)).isZero();
    }

    @Test
    @DisplayName("PASS 판정과 본인 취소가 겹쳐도 신청은 취소로 끝나고 참가권은 한 번만 돌아온다(#512)")
    void givenPass_whenScreenRacesCancel_thenCancelledAndRefundOnce() throws Exception {
        wallet(960L, 1);
        Long applicationId = applicationService.apply(960L, cohortId, FORM).applicationId();
        passWithoutEvent(960L, "FEMALE");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> screen = pool.submit(() -> {
                start.await();
                return screeningService.screenAfterIdentityVerified(960L);
            });
            Future<?> cancel = pool.submit(() -> {
                start.await();
                return applicationService.cancel(960L, applicationId);
            });
            start.countDown();
            screen.get();
            cancel.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM cohort_applications WHERE id = ?", String.class,
                applicationId)).isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM ticket_ledger WHERE reason = 'USE_REFUND' AND ref_id = ?",
                applicationId.toString())).isEqualTo(1);
        assertThat(balance(960L)).isEqualTo(1);
    }

    @Test
    @DisplayName("DB 가 진행 중 신청 2건·같은 신청의 반환 2건을 최종으로 막는다")
    void givenDuplicates_whenInsertDirectly_thenPartialUniquesReject() {
        wallet(950L, 1);
        Long applicationId = applicationService.apply(950L, cohortId, FORM).applicationId();

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO cohort_applications (user_id, cohort_id, track_id, status, school, department, answers,
                    priority, applied_at, created_at, updated_at)
                SELECT user_id, cohort_id, track_id, 'QUEUED', school, department, answers, 0, now(), now(), now()
                FROM cohort_applications WHERE id = ?""", applicationId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_cohort_applications_user_active");

        String refund = "INSERT INTO ticket_ledger (user_id, delta, balance_after, reason, ref_type, ref_id, created_at)"
                + " VALUES (950, 1, 1, 'USE_REFUND', 'COHORT_APPLICATION', ?, now())";
        jdbcTemplate.update(refund, applicationId.toString());
        assertThatThrownBy(() -> jdbcTemplate.update(refund, applicationId.toString()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_ticket_ledger_cohort_application_ref");
    }

    @Test
    @DisplayName("닫힌 상태와 닫힌 시각이 어긋나면 DB CHECK 가 막는다")
    void givenClosedWithoutClosedAt_whenUpdate_thenCheckRejects() {
        wallet(960L, 1);
        Long applicationId = applicationService.apply(960L, cohortId, FORM).applicationId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE cohort_applications SET status = 'CANCELLED' WHERE id = ?", applicationId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cohort_applications_closed");
    }

    @Test
    @DisplayName("down 은 데이터가 있으면 멈추고, 비운 뒤에는 되돌린 다음 up 을 다시 적용할 수 있다")
    void givenRows_whenDown_thenRefusesUntilEmpty() throws Exception {
        wallet(970L, 1);
        applicationService.apply(970L, cohortId, FORM);

        assertThatThrownBy(() -> executeOutsidePool(sql("511-cohort-application-down")))
                .hasMessageContaining("cohort_applications has rows");

        jdbcTemplate.execute("TRUNCATE TABLE cohort_applications, ticket_ledger");
        jdbcTemplate.update("UPDATE user_profiles SET phone = NULL");
        jdbcTemplate.execute(sql("511-cohort-application-down"));
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_name = 'user_profiles'"
                + " AND column_name = 'phone'")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('cohort_applications')::text", String.class))
                .isNull();

        // SQL 이 먼저인 배포(테이블이 없는 DB)에서도 up 이 엔티티와 같은 모양을 만든다.
        jdbcTemplate.execute(sql("511-cohort-application-up"));
        jdbcTemplate.execute(sql("511-cohort-application-verify"));
        // 테이블을 다시 만들었으니 풀 연결이 캐시한 옛 쿼리 계획을 버린다(다음 테스트의 cached plan 오류 방지).
        dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().softEvictConnections();
    }
}
