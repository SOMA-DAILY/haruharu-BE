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
 * {@code scripts/sql/449-room-mission-settings-*.sql} 을 실제 PostgreSQL 에 그대로 실행해 검증한다(402 테스트와 같은 이유).
 * <p>
 * 선행 스키마는 백필이 읽는 rooms·room_mission_slots 의 최소 형태만 재현한다.
 */
@Testcontainers
class RoomMissionSettingsMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PREREQUISITE_DDL = """
            DROP TABLE IF EXISTS room_mission_settings;
            DROP TABLE IF EXISTS room_mission_slots;
            DROP TABLE IF EXISTS rooms;

            CREATE TABLE rooms (
                id BIGSERIAL PRIMARY KEY
            );

            CREATE TABLE room_mission_slots (
                id BIGSERIAL PRIMARY KEY,
                room_id BIGINT NOT NULL REFERENCES rooms (id),
                deleted_at TIMESTAMP(6) WITH TIME ZONE
            );

            -- 1: 살아 있는 슬롯 있음 / 2: 슬롯 없음 / 3: soft-delete 된 슬롯만 있음
            INSERT INTO rooms (id) VALUES (1), (2), (3);
            INSERT INTO room_mission_slots (room_id, deleted_at) VALUES
                (1, NULL), (1, NULL),
                (3, CURRENT_TIMESTAMP);
            """;

    private static final String UP_SQL = readMigration("449-room-mission-settings-up.sql");
    private static final String DOWN_SQL = readMigration("449-room-mission-settings-down.sql");

    @BeforeEach
    void resetSchema() throws SQLException {
        execute(PREREQUISITE_DDL);
    }

    @Test
    @DisplayName("up.sql 은 살아 있는 슬롯이 있는 방만 켜짐, 나머지 방은 꺼짐으로 방당 1행 백필한다")
    void givenExistingRooms_whenApplyUp_thenBackfillsByLiveSlots() throws SQLException {
        execute(UP_SQL);

        assertThat(scalar("SELECT mission_enabled FROM room_mission_settings WHERE room_id = 1")).isEqualTo("t");
        assertThat(scalar("SELECT mission_enabled FROM room_mission_settings WHERE room_id = 2")).isEqualTo("f");
        assertThat(scalar("SELECT mission_enabled FROM room_mission_settings WHERE room_id = 3")).isEqualTo("f");
        assertThat(scalar("SELECT COUNT(*) FROM room_mission_settings")).isEqualTo("3");
    }

    @Test
    @DisplayName("up.sql 재실행은 기존 설정 행을 덮어쓰거나 중복 행을 만들지 않고, 방당 1행 제약을 건다")
    void givenExistingSetting_whenReapplyUp_thenKeepsRowAndEnforcesUniqueRoom() throws SQLException {
        execute(UP_SQL);
        execute("UPDATE room_mission_settings SET mission_enabled = false WHERE room_id = 1");

        execute(UP_SQL);

        assertThat(scalar("SELECT mission_enabled FROM room_mission_settings WHERE room_id = 1")).isEqualTo("f");
        assertThat(scalar("SELECT COUNT(*) FROM room_mission_settings")).isEqualTo("3");
        assertThatThrownBy(() -> execute("""
                INSERT INTO room_mission_settings (room_id, mission_enabled, created_at, updated_at)
                VALUES (2, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
                """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uq_room_mission_settings_room");
    }

    @Test
    @DisplayName("down.sql 은 설정 테이블을 제거하고 방·슬롯은 그대로 둔다")
    void givenAppliedUp_whenApplyDown_thenDropsSettingsOnly() throws SQLException {
        execute(UP_SQL);

        execute(DOWN_SQL);

        assertThat(scalar("SELECT to_regclass('room_mission_settings')")).isNull();
        assertThat(scalar("SELECT COUNT(*) FROM rooms")).isEqualTo("3");
        assertThat(scalar("SELECT COUNT(*) FROM room_mission_slots")).isEqualTo("3");
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
