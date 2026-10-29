package org.son.haruharu.domain.report;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the deployed #550 scripts against a legacy PostgreSQL CHECK, including an unknown future value. */
@Testcontainers
class ChildSafetyReportMigrationPostgresTest {
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
        execute(Files.readString(root.resolve("scripts/sql/550-child-safety-report-" + phase + ".sql")));
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
                    report_scope varchar(40) NOT NULL DEFAULT 'USER',
                    reason varchar(40) NOT NULL,
                    detail varchar(500),
                    status varchar(40) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED')),
                    CONSTRAINT legacy_hibernate_report_reason_check_generated CHECK
                        (reason IN ('INAPPROPRIATE_MEDIA', 'ABUSIVE_LANGUAGE', 'FAKE_PROFILE', 'SPAM_AD', 'OTHER', 'FUTURE_REASON')),
                    CONSTRAINT report_reason_length_check CHECK (char_length(reason) <= 40),
                    CONSTRAINT uq_reports_reporter_reported UNIQUE (reporter_user_id, reported_user_id, report_scope)
                );
                INSERT INTO reports (id, reporter_user_id, reported_user_id, reason, detail)
                    VALUES (1, 100, 200, 'OTHER', '기존 신고');
                """);
    }

    private void insert(long id, String reason) throws SQLException {
        execute("INSERT INTO reports(id, reporter_user_id, reported_user_id, reason) VALUES ("
                + id + ", 100, " + (200 + id) + ", '" + reason + "')");
    }

    @Test
    @DisplayName("up은 생성된 이름의 사유 CHECK에 새 값만 추가하고 기존·미래 값과 다른 제약을 보존한다")
    void givenLegacySchema_whenUpTwice_thenChildSafetyWorksAndOtherRulesRemain() throws Exception {
        assertThatThrownBy(() -> insert(2, "CHILD_SAFETY"))
                .isInstanceOf(SQLException.class).hasMessageContaining("legacy_hibernate_report_reason_check_generated");
        assertThatThrownBy(() -> script("verify"))
                .isInstanceOf(SQLException.class).hasMessageContaining("rejects CHILD_SAFETY");

        script("up");
        script("up");
        script("verify");
        insert(2, "CHILD_SAFETY");
        insert(3, "FUTURE_REASON");

        assertThat(count("SELECT count(*) FROM reports WHERE id=1 AND reason='OTHER' AND detail='기존 신고'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM reports WHERE reason='CHILD_SAFETY'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conname='report_reason_length_check'")).isEqualTo(1);
        assertThatThrownBy(() -> insert(4, "UNSUPPORTED"))
                .isInstanceOf(SQLException.class).hasMessageContaining("legacy_hibernate_report_reason_check_generated");
        assertThatThrownBy(() -> execute("INSERT INTO reports(id, reporter_user_id, reported_user_id, reason) "
                + "VALUES (5, 100, 202, 'CHILD_SAFETY')"))
                .isInstanceOf(SQLException.class).hasMessageContaining("uq_reports_reporter_reported");
        assertThatThrownBy(() -> execute("UPDATE reports SET reported_user_id=NULL WHERE id=2"))
                .isInstanceOf(SQLException.class).hasMessageContaining("not-null");
        assertThatThrownBy(() -> execute("UPDATE reports SET status='IGNORED' WHERE id=2"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reports_status_check");
    }

    @Test
    @DisplayName("down은 새 사유 접수 시 거절하며 데이터가 없을 때만 새 값을 막고 재적용할 수 있다")
    void givenChildSafetyReport_whenDown_thenRefusesWithoutDeletingData() throws Exception {
        script("up");
        insert(2, "CHILD_SAFETY");
        assertThatThrownBy(() -> script("down"))
                .isInstanceOf(SQLException.class).hasMessageContaining("CHILD_SAFETY reports exist");
        assertThat(count("SELECT count(*) FROM reports WHERE reason='CHILD_SAFETY'")).isEqualTo(1);

        execute("DELETE FROM reports WHERE reason='CHILD_SAFETY'");
        script("down");
        script("down");
        insert(3, "FUTURE_REASON");
        assertThatThrownBy(() -> insert(4, "CHILD_SAFETY"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reports_child_safety_rollback_check");
        assertThatThrownBy(() -> script("verify"))
                .isInstanceOf(SQLException.class).hasMessageContaining("rejects CHILD_SAFETY");
        script("up");
        script("verify");
        insert(4, "CHILD_SAFETY");
    }

    @Test
    @DisplayName("값 CHECK가 없는 기존 표에는 전체 허용 목록을 설치한다")
    void givenNoValueCheck_whenUp_thenInstallsAllowedReasons() throws Exception {
        execute("ALTER TABLE reports DROP CONSTRAINT legacy_hibernate_report_reason_check_generated");
        script("up");
        script("verify");
        insert(2, "CHILD_SAFETY");
        assertThatThrownBy(() -> insert(3, "UNSUPPORTED"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reports_reason_values_check");
    }

    @Test
    @DisplayName("예상하지 못한 값 CHECK가 있으면 좁히지 않고 전체 이관을 실패시킨다")
    void givenUnknownValueCheck_whenUp_thenFailsSafely() throws Exception {
        execute("""
                ALTER TABLE reports DROP CONSTRAINT legacy_hibernate_report_reason_check_generated;
                UPDATE reports SET reason='UNKNOWN_FUTURE_REASON';
                ALTER TABLE reports ADD CONSTRAINT future_only_check CHECK (reason IN ('UNKNOWN_FUTURE_REASON', 'FUTURE_ONLY'));
                """);
        assertThatThrownBy(() -> script("up"))
                .isInstanceOf(SQLException.class).hasMessageContaining("Unrecognized report reason value CHECK");
        assertThat(count("SELECT count(*) FROM reports WHERE reason='UNKNOWN_FUTURE_REASON'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conname='reports_reason_values_check'")).isZero();
    }

    @Test
    @DisplayName("추가 사용자 정의 제약이 아동 안전을 거부하면 제약을 삭제하지 않고 원자적으로 실패한다")
    void givenCustomRejection_whenUp_thenRollsBackExpansion() throws Exception {
        execute("ALTER TABLE reports ADD CONSTRAINT custom_child_rejection CHECK (reason <> 'CHILD_SAFETY')");
        assertThatThrownBy(() -> script("up"))
                .isInstanceOf(SQLException.class).hasMessageContaining("custom_child_rejection still rejects CHILD_SAFETY");
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conname='custom_child_rejection'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM reports")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conname='legacy_hibernate_report_reason_check_generated' "
                + "AND pg_get_constraintdef(oid) NOT LIKE '%CHILD_SAFETY%'")).isEqualTo(1);
    }
}
