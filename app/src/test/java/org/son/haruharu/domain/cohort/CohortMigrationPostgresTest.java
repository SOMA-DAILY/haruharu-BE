package org.son.haruharu.domain.cohort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 배포 SQL(#510)을 PostgreSQL 에 적용해 트랙·기수 제약과 되돌리기를 검증한다. */
@Testcontainers
class CohortMigrationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private long count(String sql) throws Exception {
        try (var c = connection(); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void script(String phase) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing migration scripts");
        execute(Files.readString(root.resolve("scripts/sql/510-recruit-cohort-" + phase + ".sql")));
    }

    private static String track(String key) {
        return "INSERT INTO recruit_tracks (track_key, label, region, audience, min_age, max_age, questions, display_order,"
                + " active, created_at, updated_at) VALUES ('" + key + "', '서울 대학생', '서울', 'STUDENT', 20, 29,"
                + " '[{\"key\":\"grade\",\"label\":\"학년\",\"type\":\"CHOICE\",\"required\":true,\"options\":[\"1\",\"2\"]}]',"
                + " 1, true, now(), now()) RETURNING id";
    }

    private static String cohort(long trackId, int sequence, String status) {
        return "INSERT INTO cohorts (track_id, sequence, status, apply_opens_at, apply_closes_at, assign_deadline_at,"
                + " period_label, created_at, updated_at) VALUES (" + trackId + ", " + sequence + ", '" + status + "',"
                + " '2026-10-20 10:00', '2026-10-26 00:00', '2026-10-27 12:00', '10월 마지막 주', now(), now()) RETURNING id";
    }

    @BeforeEach
    void emptySchema() throws Exception {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    }

    @Test
    @DisplayName("up 은 다시 돌려도 안전하고 verify 를 통과하며, 문항은 jsonb 로 저장된다")
    void givenEmptySchema_whenUpTwice_thenVerifies() throws Exception {
        script("up");
        script("up");
        script("verify");

        long trackId = count(track("seoul-student"));
        assertThat(count("SELECT jsonb_array_length(questions) FROM recruit_tracks WHERE id = " + trackId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conname = 'uq_cohorts_track_sequence'")).isEqualTo(1);
        // Hibernate 가 먼저 만든 테이블과 칸 타입이 같아야 한다(soft-delete 칸은 timestamptz).
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_name IN ('cohorts', 'recruit_tracks')"
                + " AND column_name = 'deleted_at' AND data_type = 'timestamp with time zone'")).isEqualTo(2);
    }

    @Test
    @DisplayName("verify 는 부분 유니크 조건·칸 타입·길이·nullable 이 어긋나면 멈춘다")
    void givenDriftedSchema_whenVerify_thenFails() throws Exception {
        script("up");
        execute("DROP INDEX ux_cohorts_track_open;"
                + "CREATE UNIQUE INDEX ux_cohorts_track_open ON cohorts (track_id) WHERE status = 'DRAFT' AND deleted_at IS NULL");
        assertThatThrownBy(() -> script("verify")).isInstanceOf(SQLException.class)
                .hasMessageContaining("ux_cohorts_track_open");

        String[] drifts = {
                "ALTER TABLE cohorts ALTER COLUMN deleted_at TYPE timestamp without time zone",
                "ALTER TABLE recruit_tracks ALTER COLUMN label TYPE varchar(80)",
                "ALTER TABLE cohorts ALTER COLUMN created_by SET NOT NULL",
                "ALTER TABLE cohorts ADD COLUMN extra integer"};
        for (String drift : drifts) {
            execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            script("up");
            execute(drift);
            assertThatThrownBy(() -> script("verify")).as(drift).isInstanceOf(SQLException.class)
                    .hasMessageContaining("columns do not match");
        }
    }

    @Test
    @DisplayName("트랙당 OPEN 기수는 DB 에서도 하나뿐이고, 다른 상태나 다른 트랙은 막지 않는다")
    void givenOpenCohort_whenAnotherOpenInSameTrack_thenPartialUniqueRejects() throws Exception {
        script("up");
        long seoul = count(track("seoul-student"));
        long gwangju = count(track("gwangju-student"));
        long sixth = count(cohort(seoul, 6, "OPEN"));

        assertThatThrownBy(() -> count(cohort(seoul, 7, "OPEN")))
                .isInstanceOf(SQLException.class).hasMessageContaining("ux_cohorts_track_open");
        count(cohort(seoul, 7, "DRAFT"));
        count(cohort(gwangju, 2, "OPEN"));
        assertThatThrownBy(() -> execute("UPDATE cohorts SET status = 'OPEN' WHERE track_id = " + seoul + " AND sequence = 7"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ux_cohorts_track_open");

        // 앞 기수를 닫으면 다음 기수를 열 수 있다. 지운(soft-delete) 기수도 자리를 차지하지 않는다.
        execute("UPDATE cohorts SET status = 'CANCELLED' WHERE id = " + sixth);
        execute("UPDATE cohorts SET status = 'OPEN' WHERE track_id = " + seoul + " AND sequence = 7");
        execute("UPDATE cohorts SET deleted_at = now() WHERE track_id = " + seoul + " AND sequence = 7");
        count(cohort(seoul, 8, "OPEN"));
    }

    @Test
    @DisplayName("같은 트랙의 같은 번호·없는 트랙·잘못된 기간·상태·나이는 DB 가 거절한다")
    void givenInvalidRows_whenInsert_thenConstraintsReject() throws Exception {
        script("up");
        long seoul = count(track("seoul-student"));
        count(cohort(seoul, 6, "DRAFT"));

        assertThatThrownBy(() -> count(track("seoul-student")))
                .isInstanceOf(SQLException.class).hasMessageContaining("uq_recruit_tracks_key");
        assertThatThrownBy(() -> count(cohort(seoul, 6, "DRAFT")))
                .isInstanceOf(SQLException.class).hasMessageContaining("uq_cohorts_track_sequence");
        assertThatThrownBy(() -> count(cohort(9999, 1, "DRAFT")))
                .isInstanceOf(SQLException.class).hasMessageContaining("fk_cohorts_track");
        assertThatThrownBy(() -> count(cohort(seoul, 0, "DRAFT")))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_cohorts_sequence");
        assertThatThrownBy(() -> count(cohort(seoul, 9, "CLOSED")))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_cohorts_status");
        assertThatThrownBy(() -> execute("UPDATE cohorts SET apply_closes_at = apply_opens_at"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_cohorts_apply_window");
        assertThatThrownBy(() -> execute("UPDATE cohorts SET assign_deadline_at = apply_closes_at - interval '1 minute'"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_cohorts_assign_deadline");
        assertThatThrownBy(() -> execute("UPDATE cohorts SET target_male = -1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_cohorts_targets");
        assertThatThrownBy(() -> execute("UPDATE recruit_tracks SET min_age = 18"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_recruit_tracks_age");
        assertThatThrownBy(() -> execute("UPDATE recruit_tracks SET audience = 'ALL'"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_recruit_tracks_audience");
        assertThatThrownBy(() -> execute("UPDATE recruit_tracks SET questions = '{}'"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_recruit_tracks_questions");
    }

    @Test
    @DisplayName("한 번도 올리지 않은 스키마에서 down 을 돌려도 실패하지 않는다")
    void givenEmptySchema_whenDown_thenNoError() throws Exception {
        script("down");
        assertThat(count("SELECT count(*) FROM pg_class WHERE relname IN ('cohorts', 'recruit_tracks')")).isZero();
    }

    @Test
    @DisplayName("트랙·기수가 남아 있으면 down 이 멈추고, 비운 뒤에는 지우고 다시 올릴 수 있다")
    void givenRows_whenDown_thenAbortsUntilCleared() throws Exception {
        script("up");
        long seoul = count(track("seoul-student"));
        count(cohort(seoul, 6, "DRAFT"));

        assertThatThrownBy(() -> script("down")).isInstanceOf(SQLException.class).hasMessageContaining("cohorts has rows");
        assertThat(count("SELECT count(*) FROM cohorts")).isEqualTo(1);

        execute("DELETE FROM cohorts");
        assertThatThrownBy(() -> script("down")).isInstanceOf(SQLException.class)
                .hasMessageContaining("recruit_tracks has rows");
        execute("DELETE FROM recruit_tracks");
        script("down");
        assertThat(count("SELECT count(*) FROM pg_class WHERE relname IN ('cohorts', 'recruit_tracks')")).isZero();
        script("down"); // 이미 내린 뒤 다시 돌려도 멈추지 않는다
        script("up");
        script("verify");
    }
}
