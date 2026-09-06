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
 * {@code scripts/sql/402-mission-dismiss-removal-*.sql} 을 실제 PostgreSQL 에 그대로 실행해 검증한다(371 테스트와 같은 이유).
 * <p>
 * 선행 스키마는 371 적용 직후 상태 — DISMISSED 를 허용하는 resolution CHECK, 활동·노출 컬럼, dismissed_member_count 를 가진
 * mission_outcomes — 의 최소 형태만 재현한다. 402 파일의 문법과 백필 재판정(활동 해소 / EXPIRED / NULL)·제약 동작 확인이 목적이다.
 */
@Testcontainers
class MissionDismissRemovalMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 371 적용 직후 상태의 최소 재현. 402 가 읽거나 건드리는 컬럼·제약만 포함한다. */
    private static final String PREREQUISITE_DDL = """
            DROP TABLE IF EXISTS mission_outcomes;
            DROP TABLE IF EXISTS mission_member_states;
            DROP TABLE IF EXISTS room_missions;
            DROP TABLE IF EXISTS room_mission_slots;

            CREATE TABLE room_mission_slots (
                id BIGSERIAL PRIMARY KEY,
                closes_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
            );

            CREATE TABLE room_missions (
                id BIGSERIAL PRIMARY KEY,
                room_mission_slot_id BIGINT REFERENCES room_mission_slots (id),
                closes_at TIMESTAMP WITHOUT TIME ZONE
            );

            CREATE TABLE mission_member_states (
                id BIGSERIAL PRIMARY KEY,
                room_mission_id BIGINT NOT NULL REFERENCES room_missions (id),
                expected_channel VARCHAR(20) NOT NULL DEFAULT 'VIDEO',
                first_impressed_at TIMESTAMP WITHOUT TIME ZONE,
                expected_activity_count INTEGER NOT NULL DEFAULT 0,
                first_expected_activity_at TIMESTAMP WITHOUT TIME ZONE,
                last_expected_activity_at TIMESTAMP WITHOUT TIME ZONE,
                resolution VARCHAR(32),
                resolved_at TIMESTAMP WITHOUT TIME ZONE,
                updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                deleted_at TIMESTAMP WITHOUT TIME ZONE,
                CONSTRAINT ck_mission_member_states_resolution
                    CHECK (resolution IS NULL OR resolution IN (
                        'DISMISSED', 'VIDEO_UPLOAD_AFTER_VIEW', 'CHAT_MESSAGE_AFTER_VIEW', 'EXPIRED')),
                CONSTRAINT ck_mission_member_states_resolution_pair
                    CHECK ((resolution IS NULL) = (resolved_at IS NULL))
            );

            CREATE TABLE mission_outcomes (
                id BIGSERIAL PRIMARY KEY,
                target_member_count INTEGER NOT NULL,
                impressed_member_count INTEGER NOT NULL,
                activated_member_count INTEGER NOT NULL,
                expected_channel_activated_count INTEGER NOT NULL,
                cross_channel_activated_count INTEGER NOT NULL,
                dismissed_member_count INTEGER NOT NULL,
                expired_member_count INTEGER NOT NULL,
                CONSTRAINT ck_mission_outcomes_counts_non_negative
                    CHECK (target_member_count >= 0
                        AND impressed_member_count >= 0
                        AND activated_member_count >= 0
                        AND expected_channel_activated_count >= 0
                        AND cross_channel_activated_count >= 0
                        AND dismissed_member_count >= 0
                        AND expired_member_count >= 0),
                CONSTRAINT ck_mission_outcomes_counts_within_target
                    CHECK (impressed_member_count <= target_member_count
                        AND activated_member_count <= target_member_count
                        AND expected_channel_activated_count <= target_member_count
                        AND cross_channel_activated_count <= target_member_count
                        AND dismissed_member_count <= target_member_count
                        AND expired_member_count <= target_member_count)
            );

            -- 미션 1: 이미 닫힌 창(1일 전 마감, 슬롯 closes_at 만 있음) / 미션 2: 아직 열린 창(1일 뒤 마감, room_missions.closes_at)
            INSERT INTO room_mission_slots (id, closes_at) VALUES (1, CURRENT_TIMESTAMP - INTERVAL '1 day');
            INSERT INTO room_missions (id, room_mission_slot_id, closes_at) VALUES (1, 1, NULL);
            INSERT INTO room_missions (id, room_mission_slot_id, closes_at) VALUES (2, NULL, CURRENT_TIMESTAMP + INTERVAL '1 day');
            """;

    private static final String UP_SQL = readMigration("402-mission-dismiss-removal-up.sql");
    private static final String DOWN_SQL = readMigration("402-mission-dismiss-removal-down.sql");

    @BeforeEach
    void resetSchema() throws SQLException {
        execute(PREREQUISITE_DDL);
    }

    @Test
    @DisplayName("up.sql 은 DISMISSED 카드를 활동 해소 / 닫힌 창은 EXPIRED / 열린 창은 미해소로 재판정한다")
    void givenDismissedRows_whenApplyUp_thenReinterpretedByNewPolicy() throws SQLException {
        execute("""
                INSERT INTO mission_member_states
                    (id, room_mission_id, expected_channel, first_impressed_at,
                     expected_activity_count, first_expected_activity_at, last_expected_activity_at,
                     resolution, resolved_at)
                VALUES
                    -- 1: 닫힌 미션, 노출 후 기대 채널(VIDEO) 활동 → 활동 해소, resolved_at = 노출 이후 첫 활동
                    (1, 1, 'VIDEO', TIMESTAMP '2026-09-01 10:00', 1,
                        TIMESTAMP '2026-09-01 10:30', TIMESTAMP '2026-09-01 10:30',
                        'DISMISSED', TIMESTAMP '2026-09-01 10:05'),
                    -- 2: 닫힌 미션, 노출만(활동 없음) → EXPIRED, resolved_at = 슬롯 closes_at
                    (2, 1, 'VIDEO', TIMESTAMP '2026-09-01 10:00', 0, NULL, NULL,
                        'DISMISSED', TIMESTAMP '2026-09-01 10:05'),
                    -- 3: 닫힌 미션, 활동이 노출보다 먼저(recordActivity 규칙상 카드 해소 아님) → EXPIRED
                    (3, 1, 'GROUP_CHAT', TIMESTAMP '2026-09-01 10:00', 1,
                        TIMESTAMP '2026-09-01 09:00', TIMESTAMP '2026-09-01 09:00',
                        'DISMISSED', TIMESTAMP '2026-09-01 10:05'),
                    -- 4: 열린 미션, 활동 없음 → NULL/NULL (재노출)
                    (4, 2, 'VIDEO', TIMESTAMP '2026-09-01 10:00', 0, NULL, NULL,
                        'DISMISSED', TIMESTAMP '2026-09-01 10:05'),
                    -- 5: 열린 미션, 노출 후 GROUP_CHAT 활동 → 활동 해소
                    (5, 2, 'GROUP_CHAT', TIMESTAMP '2026-09-01 10:00', 2,
                        TIMESTAMP '2026-09-01 11:00', TIMESTAMP '2026-09-01 12:00',
                        'DISMISSED', TIMESTAMP '2026-09-01 10:05'),
                    -- 6: DISMISSED 가 아닌 행은 건드리지 않는다
                    (6, 2, 'VIDEO', NULL, 0, NULL, NULL,
                        'VIDEO_UPLOAD_AFTER_VIEW', TIMESTAMP '2026-09-01 10:05');
                -- id 를 명시했으므로 시퀀스를 따라 올려 이후 INSERT 의 PK 중복을 막는다
                SELECT setval(pg_get_serial_sequence('mission_member_states', 'id'), 6);
                """);

        // when
        execute(UP_SQL);

        // then
        assertThat(scalar("SELECT COUNT(*) FROM mission_member_states WHERE resolution = 'DISMISSED'")).isEqualTo("0");
        assertThat(scalar("SELECT resolution || '@' || resolved_at FROM mission_member_states WHERE id = 1"))
                .isEqualTo("VIDEO_UPLOAD_AFTER_VIEW@2026-09-01 10:30:00");
        assertThat(scalar("SELECT resolution FROM mission_member_states WHERE id = 2")).isEqualTo("EXPIRED");
        assertThat(scalar("""
                SELECT resolved_at = (SELECT closes_at FROM room_mission_slots WHERE id = 1)
                FROM mission_member_states WHERE id = 2
                """)).isEqualTo("t");
        assertThat(scalar("SELECT resolution FROM mission_member_states WHERE id = 3")).isEqualTo("EXPIRED");
        assertThat(scalar("SELECT resolution IS NULL AND resolved_at IS NULL FROM mission_member_states WHERE id = 4"))
                .isEqualTo("t");
        assertThat(scalar("SELECT resolution || '@' || resolved_at FROM mission_member_states WHERE id = 5"))
                .isEqualTo("CHAT_MESSAGE_AFTER_VIEW@2026-09-01 11:00:00");
        assertThat(scalar("SELECT resolution || '@' || resolved_at FROM mission_member_states WHERE id = 6"))
                .isEqualTo("VIDEO_UPLOAD_AFTER_VIEW@2026-09-01 10:05:00");

        // 재정의된 제약이 DISMISSED 를 거부하고 EXPIRED 는 계속 허용한다
        assertThatThrownBy(() -> execute("""
                INSERT INTO mission_member_states (room_mission_id, resolution, resolved_at)
                VALUES (2, 'DISMISSED', CURRENT_TIMESTAMP);
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_mission_member_states_resolution");
        execute("""
                INSERT INTO mission_member_states (room_mission_id, resolution, resolved_at)
                VALUES (2, 'EXPIRED', CURRENT_TIMESTAMP);
                """);
    }

    @Test
    @DisplayName("up.sql 은 mission_outcomes 에서 dismissed_member_count 를 제거하고 CHECK 를 재정의한다")
    void givenOutcomeSchema_whenApplyUp_thenDropsDismissedColumnAndKeepsChecks() throws SQLException {
        execute(UP_SQL);

        assertThat(scalar("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'mission_outcomes' AND column_name = 'dismissed_member_count'
                """)).isEqualTo("0");

        // 재정의된 CHECK 가 여전히 동작한다(부분 카운트 > 대상)
        assertThatThrownBy(() -> execute("""
                INSERT INTO mission_outcomes (target_member_count, impressed_member_count, activated_member_count,
                    expected_channel_activated_count, cross_channel_activated_count, expired_member_count)
                VALUES (3, 3, 5, 0, 0, 0);
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_mission_outcomes_counts_within_target");
        execute("""
                INSERT INTO mission_outcomes (target_member_count, impressed_member_count, activated_member_count,
                    expected_channel_activated_count, cross_channel_activated_count, expired_member_count)
                VALUES (3, 3, 2, 2, 0, 1);
                """);
    }

    @Test
    @DisplayName("down.sql 은 dismissed_member_count 를 0 기본값으로 복원하고 DISMISSED 를 다시 허용한다")
    void givenAppliedMigration_whenApplyDown_thenRestoresColumnAndAllowsDismissed() throws SQLException {
        execute(UP_SQL);
        execute("""
                INSERT INTO mission_outcomes (target_member_count, impressed_member_count, activated_member_count,
                    expected_channel_activated_count, cross_channel_activated_count, expired_member_count)
                VALUES (3, 3, 2, 2, 0, 1);
                """);

        // when
        execute(DOWN_SQL);

        // then
        assertThat(scalar("SELECT dismissed_member_count FROM mission_outcomes")).isEqualTo("0");
        execute("""
                INSERT INTO mission_member_states (room_mission_id, resolution, resolved_at)
                VALUES (2, 'DISMISSED', CURRENT_TIMESTAMP);
                """);
        assertThat(scalar("SELECT COUNT(*) FROM mission_member_states WHERE resolution = 'DISMISSED'"))
                .isEqualTo("1");
    }

    // --- helpers ---

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
