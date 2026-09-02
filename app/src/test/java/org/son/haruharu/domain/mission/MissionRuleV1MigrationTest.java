package org.son.haruharu.domain.mission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code scripts/sql/379-mission-rule-v1-*.sql} 을 실제 PostgreSQL 에 그대로 실행해 검증한다.
 * <p>
 * Rule V1 이 완화 단계를 2개에서 6개로 늘렸는데, 365 가 만든 CHECK 제약은 여전히 2개만 허용했다.
 * Hibernate 는 {@code @Check} 를 쓰지 않는 한 CHECK 제약을 만들지도 고치지도 않으므로
 * {@code ddl-auto} 로는 절대 갱신되지 않는다. 테스트 스키마는 {@code create-drop} 이라 제약 자체가
 * 없어서 <b>단위 테스트도 통합 테스트도 이 불일치를 잡지 못한다.</b> 그래서 파일 원문을 직접 돌린다.
 */
@Testcontainers
class MissionRuleV1MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 379 가 ALTER 하는 대상. 365 의 해당 부분만 옮겨온 최소 형태다. */
    private static final String PREREQUISITE_DDL = """
            DROP TABLE IF EXISTS room_missions;

            CREATE TABLE room_missions (
                id BIGSERIAL PRIMARY KEY,
                relaxation_level VARCHAR(24) NOT NULL,
                updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT ck_room_missions_relaxation
                    CHECK (relaxation_level IN ('NONE', 'SAFE_FALLBACK'))
            );
            """;

    private static final String UP_SQL = readMigration("379-mission-rule-v1-up.sql");
    private static final String DOWN_SQL = readMigration("379-mission-rule-v1-down.sql");

    @BeforeEach
    void resetSchema() throws SQLException {
        execute(PREREQUISITE_DDL);
    }

    @Test
    @DisplayName("마이그레이션 전에는 새 완화 단계가 거부된다 — 이 이슈가 고치려는 상태")
    void givenPreMigrationSchema_whenInsertNewLevel_thenRejected() {
        assertThatThrownBy(() -> execute(insert("CLUSTER_RELAXED")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_room_missions_relaxation");
    }

    @Test
    @DisplayName("up.sql 적용 후 enum 의 모든 완화 단계가 저장된다")
    void givenMigratedSchema_whenInsertEveryLevel_thenAllAccepted() throws SQLException {
        execute(UP_SQL);

        for (MissionAssignmentRelaxationLevel level : MissionAssignmentRelaxationLevel.values()) {
            assertThatCode(() -> execute(insert(level.name())))
                    .as("%s 는 저장할 수 있어야 한다", level)
                    .doesNotThrowAnyException();
        }
        assertThat(count()).isEqualTo(String.valueOf(MissionAssignmentRelaxationLevel.values().length));
    }

    @Test
    @DisplayName("허용 목록 밖의 값은 마이그레이션 후에도 거부된다")
    void givenMigratedSchema_whenInsertUnknownLevel_thenRejected() throws SQLException {
        execute(UP_SQL);

        assertThatThrownBy(() -> execute(insert("SOMETHING_ELSE")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_room_missions_relaxation");
    }

    @Test
    @DisplayName("down.sql 은 새 단계 값을 NONE 으로 정리한 뒤 제약을 원래대로 되돌린다")
    void givenMigratedData_whenApplyDown_thenNormalizesAndReverts() throws SQLException {
        execute(UP_SQL);
        execute(insert("CLUSTER_RELAXED"));
        execute(insert("REUSE_RELAXED"));
        execute(insert("SAFE_FALLBACK"));

        execute(DOWN_SQL);

        assertThat(scalar("SELECT COUNT(*) FROM room_missions WHERE relaxation_level = 'NONE'"))
                .as("되돌린 제약이 허용하지 않는 값은 먼저 비워져야 한다").isEqualTo("2");
        assertThat(scalar("SELECT COUNT(*) FROM room_missions WHERE relaxation_level = 'SAFE_FALLBACK'"))
                .as("원래 허용되던 값은 보존된다").isEqualTo("1");
        assertThatThrownBy(() -> execute(insert("CLUSTER_RELAXED")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_room_missions_relaxation");
    }

    // --- helpers ---

    private static String insert(String relaxationLevel) {
        return "INSERT INTO room_missions (relaxation_level) VALUES ('%s');".formatted(relaxationLevel);
    }

    private static String count() throws SQLException {
        return scalar("SELECT COUNT(*) FROM room_missions");
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
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
