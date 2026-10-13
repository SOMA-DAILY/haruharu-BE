package org.son.haruharu.domain.chat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ChatCloseAtMigrationPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private Connection connection;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        execute("CREATE SCHEMA migration_test");
        execute("SET search_path TO migration_test");
        execute("CREATE TABLE rooms (id bigint primary key, status varchar(20), deleted_at timestamp)");
        execute("CREATE TABLE chat_rooms (id bigint primary key, type varchar(10), room_id bigint, deleted_at timestamp, updated_at timestamp)");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection != null) {
            try { execute("DROP SCHEMA migration_test CASCADE"); }
            finally { connection.close(); }
        }
    }

    @Test
    void migrationPreservesOpenDirectAndExistingClosureAndClosesTerminalOrphans() throws Exception {
        execute("""
                INSERT INTO rooms VALUES (1,'ACTIVE',null),(2,'SELECTING',null),(3,'ENDED',null),
                (4,'CANCELLED',null),(5,'ACTIVE',localtimestamp),(6,'PENDING',null);
                INSERT INTO chat_rooms (id,type,room_id) VALUES
                (1,'GROUP',1),(2,'GROUP',2),(3,'GROUP',3),(4,'GROUP',4),(5,'GROUP',5),
                (7,'GROUP',999),(8,'DIRECT',null),(9,'GROUP',3),(10,'GROUP',null);
                """);
        script("up");
        script("up");
        execute("UPDATE chat_rooms SET close_at = timestamp '2026-01-01 00:00:00' WHERE id=9");
        assertThatThrownBy(() -> script("verify")).isInstanceOf(SQLException.class);
        script("backfill");
        script("verify");
        assertThat(count("close_at IS NOT NULL")).isEqualTo(6);
        assertThat(count("id IN (1,2,8) AND close_at IS NULL")).isEqualTo(3);
        assertThat(count("id=9 AND close_at=timestamp '2026-01-01 00:00:00'")).isEqualTo(1);
        execute("CREATE TEMP TABLE first_closure AS SELECT id,close_at FROM chat_rooms");
        script("backfill");
        assertThat(scalar("SELECT count(*) FROM chat_rooms c JOIN first_closure f USING(id) WHERE c.close_at IS DISTINCT FROM f.close_at"))
                .isZero();
        execute("INSERT INTO chat_rooms (id,type,room_id) VALUES (6,'GROUP',6)");
        assertThatThrownBy(() -> script("verify")).isInstanceOf(SQLException.class)
                .hasMessageContaining("unexpected source status");
    }

    @Test
    void backfillIsBoundedAndVerificationRejectsIncompleteBatches() throws Exception {
        execute("INSERT INTO rooms SELECT n,'ENDED',null FROM generate_series(1,1100) n");
        execute("INSERT INTO chat_rooms (id,type,room_id) SELECT n,'GROUP',n FROM generate_series(1,1100) n");
        script("up");
        script("backfill");
        assertThat(count("close_at IS NOT NULL")).isEqualTo(1000);
        assertThatThrownBy(() -> script("verify")).isInstanceOf(SQLException.class);
        script("backfill");
        script("verify");
        assertThat(count("close_at IS NOT NULL")).isEqualTo(1100);
    }

    private void script(String action) throws Exception {
        execute(Files.readString(Path.of("..", "scripts", "sql", "502-chat-close-at-" + action + ".sql")));
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private long count(String predicate) throws SQLException {
        return scalar("SELECT count(*) FROM chat_rooms WHERE " + predicate);
    }

    private long scalar(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
