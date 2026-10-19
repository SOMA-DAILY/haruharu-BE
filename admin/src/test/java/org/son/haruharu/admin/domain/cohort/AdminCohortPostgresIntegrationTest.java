package org.son.haruharu.admin.domain.cohort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.business.CohortService;
import org.son.haruharu.domain.cohort.business.RecruitTrackService;
import org.son.haruharu.domain.cohort.business.command.CreateCohortCommand;
import org.son.haruharu.domain.cohort.business.command.CreateRecruitTrackCommand;
import org.son.haruharu.domain.cohort.business.exception.CohortOpenAlreadyExistsException;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;
import org.son.haruharu.domain.cohort.implement.TrackQuestionType;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #510 실제 PostgreSQL 검증. Hibernate 가 만든 테이블 위에 up.sql 을 덮어 "새 버전을 먼저 띄운 경우"도 맞는지 보고,
 * 같은 트랙의 두 기수를 동시에 공개하면 하나만 성공하는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminCohortPostgresIntegrationTest {

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

    @Autowired RecruitTrackService trackService;
    @Autowired CohortService cohortService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void applyMigrationOverHibernateSchema() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE cohorts, recruit_tracks RESTART IDENTITY CASCADE");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing migration scripts");
        jdbcTemplate.execute(Files.readString(root.resolve("scripts/sql/510-recruit-cohort-up.sql")));
        jdbcTemplate.execute(Files.readString(root.resolve("scripts/sql/510-recruit-cohort-verify.sql")));
    }

    private Long track(String key) {
        return trackService.create(new CreateRecruitTrackCommand(key, "서울 대학생", "서울", RecruitAudience.STUDENT,
                20, 29, List.of(new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE, true, List.of("1", "2"))),
                1)).id();
    }

    private Long draft(Long trackId, int sequence) {
        LocalDateTime now = LocalDateTime.now();
        return cohortService.create(new CreateCohortCommand(trackId, sequence, now.minusDays(1), now.plusDays(3),
                now.plusDays(4), "10월 마지막 주", 6, 6), "admin:1").id();
    }

    @Test
    @DisplayName("Hibernate 스키마 위에 up 을 덮어도 verify 를 통과하고, 문항은 jsonb 로 왕복한다")
    void givenHibernateSchema_whenUp_thenVerifiesAndJsonbRoundTrips() {
        Long trackId = track("seoul-student");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'recruit_tracks' AND column_name = 'questions'",
                String.class)).isEqualTo("jsonb");
        // Hibernate 가 붙인 enum CHECK 는 걷히고 ck_* 하나만 남는다(값을 늘릴 때 고칠 곳이 하나).
        assertThat(jdbcTemplate.queryForList("""
                SELECT con.conname FROM pg_constraint con
                JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ANY (con.conkey)
                WHERE con.contype = 'c' AND ((con.conrelid = 'cohorts'::regclass AND att.attname = 'status')
                   OR (con.conrelid = 'recruit_tracks'::regclass AND att.attname = 'audience'))""", String.class))
                .containsExactlyInAnyOrder("ck_cohorts_status", "ck_recruit_tracks_audience");
        assertThat(trackService.getTracks()).singleElement()
                .satisfies(track -> assertThat(track.questions().get(0).options()).containsExactly("1", "2"));
        // 조건 없는 목록(null 파라미터)도 PostgreSQL 에서 타입 추론 오류 없이 돈다.
        draft(trackId, 6);
        assertThat(cohortService.getCohorts(null, null)).hasSize(1);
        assertThat(cohortService.getCohorts(trackId, CohortStatus.DRAFT)).hasSize(1);
    }

    @Test
    @DisplayName("같은 트랙의 두 기수를 동시에 공개하면 하나만 OPEN 이 된다")
    void givenTwoDrafts_whenPublishConcurrently_thenOnlyOneOpens() throws Exception {
        Long trackId = track("seoul-student");
        List<Long> drafts = List.of(draft(trackId, 6), draft(trackId, 7));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Long cohortId : drafts) {
                Callable<Boolean> publish = () -> {
                    start.await();
                    try {
                        cohortService.publish(cohortId);
                        return true;
                    } catch (CohortOpenAlreadyExistsException e) {
                        return false;
                    }
                };
                results.add(pool.submit(publish));
            }
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> result : results) if (result.get()) succeeded++;

            assertThat(succeeded).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM cohorts WHERE track_id = ? AND status = 'OPEN'", Long.class, trackId))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
