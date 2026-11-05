package org.son.haruharu.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the #553 scripts against a legacy PostgreSQL status CHECK (status IN ('RECEIVED')). */
@Testcontainers
class ReportResolutionMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void execute(String sql) throws SQLException {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private long count(String sql) throws SQLException {
        try (var c = connection(); var s = c.createStatement(); var result = s.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private void script(String phase) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing migration scripts");
        execute(Files.readString(root.resolve("scripts/sql/553-report-resolution-" + phase + ".sql")));
    }

    @BeforeEach
    void legacySchema() throws SQLException {
        execute("""
                DROP SCHEMA public CASCADE;
                CREATE SCHEMA public;
                CREATE TABLE reports (
                    id bigint PRIMARY KEY,
                    reporter_user_id bigint NOT NULL,
                    reported_user_id bigint NOT NULL,
                    reason varchar(40) NOT NULL,
                    status varchar(40) NOT NULL DEFAULT 'RECEIVED',
                    CONSTRAINT reports_status_check CHECK (status IN ('RECEIVED'))
                );
                INSERT INTO reports (id, reporter_user_id, reported_user_id, reason) VALUES (1, 100, 200, 'OTHER');
                """);
    }

    @Test
    @DisplayName("up은 status CHECK에 제재·기각만 추가하고 두 번 돌려도 안전하며 알 수 없는 값은 계속 막는다")
    void givenLegacyCheck_whenUpTwice_thenResolvedStatusesAllowed() throws Exception {
        assertThatThrownBy(() -> execute("UPDATE reports SET status='SANCTIONED' WHERE id=1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reports_status_check");
        assertThatThrownBy(() -> script("verify")).isInstanceOf(SQLException.class).hasMessageContaining("rejects SANCTIONED");

        script("up");
        script("up");
        script("verify");
        execute("UPDATE reports SET status='SANCTIONED', resolved_by_admin_id=1, resolved_at=now() WHERE id=1");
        execute("INSERT INTO reports (id, reporter_user_id, reported_user_id, reason, status) VALUES (2, 100, 201, 'OTHER', 'REJECTED')");
        assertThat(count("SELECT count(*) FROM reports WHERE status IN ('SANCTIONED','REJECTED')")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_name='reports' "
                + "AND column_name IN ('resolved_by_admin_id','resolved_at')")).isEqualTo(2);
        assertThatThrownBy(() -> execute("UPDATE reports SET status='IGNORED' WHERE id=1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reports_status_check");
    }

    @Test
    @DisplayName("down은 처리된 신고가 있으면 거절하고, 없으면 RECEIVED 만 받게 되돌리며 up으로 다시 올릴 수 있다")
    void givenResolvedReport_whenDown_thenRefuses() throws Exception {
        script("up");
        execute("UPDATE reports SET status='REJECTED' WHERE id=1");
        assertThatThrownBy(() -> script("down")).isInstanceOf(SQLException.class).hasMessageContaining("violated by some row");

        execute("UPDATE reports SET status='RECEIVED' WHERE id=1");
        script("down");
        assertThatThrownBy(() -> execute("UPDATE reports SET status='SANCTIONED' WHERE id=1"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reports_status_check");
        script("up");
        script("verify");
        execute("UPDATE reports SET status='SANCTIONED' WHERE id=1");
    }
}
