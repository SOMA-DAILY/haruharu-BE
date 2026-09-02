package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code scripts/sql/371-mission-outcomes-*.sql} 을 실제 PostgreSQL 에 그대로 실행해 검증한다.
 * <p>
 * 다른 통합 테스트는 {@code ddl-auto: create-drop} 으로 JPA 엔티티에서 스키마를 만들기 때문에,
 * 손으로 쓴 마이그레이션 SQL 과 CHECK 제약은 어디에서도 실행되지 않는다. 운영에 적용하는 순간
 * 처음 검증되는 셈이라 여기서 파일 원문을 직접 돌린다.
 * <p>
 * 선행 객체(room_missions, mission_member_states)는 371 이 ALTER·FK 로 참조하는 최소 형태만 만든다.
 * 전체 스키마 재현이 목적이 아니라 371 파일 자체의 문법과 제약 동작을 확인하는 것이 목적이다.
 */
@Testcontainers
class MissionOutcomeMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 371 이 ALTER 로 건드리고 FK 로 참조하는 선행 객체. 367 의 해당 부분만 옮겨온 최소 형태다. */
    private static final String PREREQUISITE_DDL = """
            DROP TABLE IF EXISTS mission_outcomes;
            DROP TABLE IF EXISTS mission_member_states;
            DROP TABLE IF EXISTS room_missions;

            CREATE TABLE room_missions (
                id BIGSERIAL PRIMARY KEY
            );

            CREATE TABLE mission_member_states (
                id BIGSERIAL PRIMARY KEY,
                resolution VARCHAR(32),
                resolved_at TIMESTAMP WITHOUT TIME ZONE,
                updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TIMESTAMP WITHOUT TIME ZONE,
                CONSTRAINT ck_mission_member_states_resolution
                    CHECK (resolution IS NULL OR resolution IN (
                        'DISMISSED', 'VIDEO_UPLOAD_AFTER_VIEW', 'CHAT_MESSAGE_AFTER_VIEW')),
                CONSTRAINT ck_mission_member_states_resolution_pair
                    CHECK ((resolution IS NULL) = (resolved_at IS NULL))
            );

            INSERT INTO room_missions DEFAULT VALUES;
            """;

    private static final String UP_SQL = readMigration("371-mission-outcomes-up.sql");
    private static final String DOWN_SQL = readMigration("371-mission-outcomes-down.sql");

    @BeforeEach
    void resetSchema() throws SQLException {
        execute(PREREQUISITE_DDL);
    }

    @Test
    @DisplayName("up.sql 은 mission_outcomes 를 만들고 mission_member_states 가 EXPIRED 를 허용하게 한다")
    void givenPrerequisiteSchema_whenApplyUp_thenCreatesTableAndAllowsExpired() throws SQLException {
        // when
        execute(UP_SQL);

        // then
        assertThat(bool("SELECT to_regclass('public.mission_outcomes') IS NOT NULL")).isTrue();
        assertThat(scalar("""
                SELECT COUNT(*) FROM pg_constraint
                WHERE conname = 'uq_mission_outcomes_mission_metric_version'
                """)).isEqualTo("1");
        assertThat(scalar("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE indexname = 'idx_mission_outcomes_closes_at'
                """)).isEqualTo("1");

        // EXPIRED 가 허용된다
        execute("""
                INSERT INTO mission_member_states (resolution, resolved_at)
                VALUES ('EXPIRED', CURRENT_TIMESTAMP);
                """);
        assertThat(scalar("SELECT COUNT(*) FROM mission_member_states WHERE resolution = 'EXPIRED'"))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("부분 카운트가 대상 멤버 수를 넘으면 CHECK 제약이 거부한다")
    void givenCountExceedingTarget_whenInsert_thenRejected() throws SQLException {
        execute(UP_SQL);

        assertThatThrownBy(() -> execute(outcomeInsert(
                /* target */ 3, /* impressed */ 3, /* activated */ 5,
                "0.5000", "1.0000")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_mission_outcomes_counts_within_target");
    }

    @Test
    @DisplayName("대상 멤버가 0인데 비율이 0이 아니면 CHECK 제약이 거부한다")
    void givenZeroTargetWithNonZeroRatio_whenInsert_thenRejected() throws SQLException {
        execute(UP_SQL);

        assertThatThrownBy(() -> execute(outcomeInsert(0, 0, 0, "0.5000", "0.0000")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_mission_outcomes_zero_target_zero_ratio");
    }

    @Test
    @DisplayName("비율이 0~1 범위를 벗어나면 CHECK 제약이 거부한다")
    void givenRatioOutOfRange_whenInsert_thenRejected() throws SQLException {
        execute(UP_SQL);

        assertThatThrownBy(() -> execute(outcomeInsert(3, 3, 3, "1.5000", "1.0000")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_mission_outcomes_ratio_range");
    }

    @Test
    @DisplayName("같은 미션·같은 집계 버전은 유니크 제약이 두 번 확정되지 못하게 막는다")
    void givenSameMissionAndVersion_whenInsertTwice_thenRejected() throws SQLException {
        execute(UP_SQL);
        execute(outcomeInsert(3, 3, 3, "1.0000", "1.0000"));

        assertThatThrownBy(() -> execute(outcomeInsert(3, 3, 3, "1.0000", "1.0000")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uq_mission_outcomes_mission_metric_version");
    }

    @Test
    @DisplayName("정상 범위 집계는 그대로 저장된다")
    void givenValidCounts_whenInsert_thenStored() throws SQLException {
        execute(UP_SQL);

        execute(outcomeInsert(6, 5, 4, "0.6667", "0.8333"));

        assertThat(scalar("SELECT room_activation_coverage FROM mission_outcomes")).isEqualTo("0.6667");
        assertThat(scalar("SELECT exposure_reach FROM mission_outcomes")).isEqualTo("0.8333");
    }

    @Test
    @DisplayName("down.sql 은 테이블을 제거하고 EXPIRED 를 정리한 뒤 제약을 원래대로 되돌린다")
    void givenAppliedMigration_whenApplyDown_thenRevertsCleanly() throws SQLException {
        execute(UP_SQL);
        execute("""
                INSERT INTO mission_member_states (resolution, resolved_at)
                VALUES ('EXPIRED', CURRENT_TIMESTAMP);
                """);

        // when
        execute(DOWN_SQL);

        // then
        assertThat(bool("SELECT to_regclass('public.mission_outcomes') IS NULL")).isTrue();
        // EXPIRED 였던 행은 제약을 되돌리기 전에 비워져 있어야 한다
        assertThat(scalar("SELECT COUNT(*) FROM mission_member_states WHERE resolution IS NOT NULL"))
                .isEqualTo("0");
        // 되돌린 제약이 다시 EXPIRED 를 거부한다
        assertThatThrownBy(() -> execute("""
                INSERT INTO mission_member_states (resolution, resolved_at)
                VALUES ('EXPIRED', CURRENT_TIMESTAMP);
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_mission_member_states_resolution");
    }

    // --- helpers ---

    private static String outcomeInsert(
            int target, int impressed, int activated, String coverage, String reach) {
        return """
                INSERT INTO mission_outcomes (
                    room_mission_id, metric_schema_version,
                    target_member_count, impressed_member_count, activated_member_count,
                    expected_channel_activated_count, cross_channel_activated_count,
                    dismissed_member_count, expired_member_count,
                    room_activation_coverage, exposure_reach,
                    closes_at, calculated_at, created_at, updated_at)
                SELECT rm.id, 'MISSION_OUTCOME_V1',
                       %d, %d, %d, 0, 0, 0, 0,
                       %s, %s,
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM room_missions rm LIMIT 1;
                """.formatted(target, impressed, activated, coverage, reach);
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** boolean 컬럼은 getString 이 "t"/"f" 를 돌려주므로 별도 헬퍼로 읽는다. */
    private static boolean bool(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private static String scalar(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** 모듈 작업 디렉터리에서 위로 올라가며 scripts/sql 을 찾는다. */
    private static String readMigration(String fileName) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.isDirectory(directory.resolve("scripts/sql"))) {
            directory = directory.getParent();
        }
        if (directory == null) {
            throw new IllegalStateException("scripts/sql 디렉터리를 찾지 못했다");
        }
        try {
            return Files.readString(directory.resolve("scripts/sql").resolve(fileName),
                    StandardCharsets.UTF_8);
        } catch (IOException readFailure) {
            throw new UncheckedIOException(readFailure);
        }
    }
}
