package org.son.haruharu.domain.chat;

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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code 376-chat-unread-indexes-up.sql} 원문을 PostgreSQL에서 실행해 membership 복구를 검증한다. */
@Testcontainers
class ChatGroupMembershipMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PREREQUISITE_DDL = """
            DROP TABLE IF EXISTS chat_messages;
            DROP TABLE IF EXISTS chat_room_members;
            DROP TABLE IF EXISTS chat_rooms;
            DROP TABLE IF EXISTS room_members;
            DROP TABLE IF EXISTS rooms;

            CREATE TABLE rooms (
                id BIGINT PRIMARY KEY,
                status VARCHAR(20) NOT NULL,
                deleted_at TIMESTAMP WITHOUT TIME ZONE
            );

            CREATE TABLE room_members (
                id BIGSERIAL PRIMARY KEY,
                room_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                deleted_at TIMESTAMP WITHOUT TIME ZONE,
                CONSTRAINT uk_room_member_room_user UNIQUE (room_id, user_id)
            );

            CREATE TABLE chat_rooms (
                id BIGINT PRIMARY KEY,
                type VARCHAR(10) NOT NULL,
                room_id BIGINT,
                deleted_at TIMESTAMP WITHOUT TIME ZONE
            );

            CREATE TABLE chat_room_members (
                id BIGSERIAL PRIMARY KEY,
                chat_room_id BIGINT NOT NULL,
                user_id BIGINT NOT NULL,
                joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                left_at TIMESTAMP WITHOUT TIME ZONE,
                last_read_message_id BIGINT,
                CONSTRAINT uq_chat_room_members_room_user UNIQUE (chat_room_id, user_id)
            );

            CREATE TABLE chat_messages (
                id BIGSERIAL PRIMARY KEY,
                chat_room_id BIGINT NOT NULL,
                created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                deleted_at TIMESTAMP WITHOUT TIME ZONE
            );

            INSERT INTO rooms (id, status, deleted_at) VALUES
                (1, 'ACTIVE', NULL),
                (2, 'SELECTING', NULL),
                (3, 'PENDING', NULL),
                (4, 'ACTIVE', TIMESTAMP '2026-09-02 10:00:00'),
                (5, 'ACTIVE', NULL);

            INSERT INTO chat_rooms (id, type, room_id, deleted_at) VALUES
                (11, 'GROUP', 1, NULL),
                (12, 'GROUP', 2, NULL),
                (13, 'GROUP', 3, NULL),
                (14, 'GROUP', 4, NULL),
                (15, 'GROUP', 5, TIMESTAMP '2026-09-02 10:00:00');

            INSERT INTO room_members (room_id, user_id, joined_at, deleted_at) VALUES
                (1, 101, TIMESTAMP '2026-09-02 09:05:00', NULL),
                (1, 102, TIMESTAMP '2026-09-02 09:10:00', NULL),
                (1, 103, TIMESTAMP '2026-09-02 09:15:00', TIMESTAMP '2026-09-02 10:00:00'),
                (2, 201, TIMESTAMP '2026-09-02 12:05:00', NULL),
                (3, 301, TIMESTAMP '2026-09-02 08:05:00', NULL),
                (4, 401, TIMESTAMP '2026-09-02 09:05:00', NULL),
                (5, 501, TIMESTAMP '2026-09-02 09:05:00', NULL);

            INSERT INTO chat_room_members (
                chat_room_id, user_id, joined_at, left_at, last_read_message_id)
            VALUES (11, 102, TIMESTAMP '2026-09-02 08:00:00',
                    TIMESTAMP '2026-09-02 10:00:00', 77);
            """;

    private static final String UP_SQL = readMigration("376-chat-unread-indexes-up.sql");

    @BeforeEach
    void resetSchema() throws SQLException {
        execute(PREREQUISITE_DDL);
    }

    @Test
    @DisplayName("up.sql은 ACTIVE·SELECTING의 누락 GROUP 멤버를 백필하고 재실행해도 퇴장·삭제 상태를 바꾸지 않는다")
    void givenMissingAndInactiveMemberships_whenApplyUpTwice_thenBackfillsIdempotentlyWithoutReactivation()
            throws SQLException {
        execute(UP_SQL);

        assertThat(countMembership(11, 101)).isOne();
        assertThat(joinedAt(11, 101)).isEqualTo(LocalDateTime.of(2026, 9, 2, 9, 5));
        assertThat(countMembership(12, 201)).isOne();

        assertThat(countMembership(13, 301)).as("PENDING 방은 백필하지 않는다").isZero();
        assertThat(countMembership(14, 401)).as("삭제된 방은 백필하지 않는다").isZero();
        assertThat(countMembership(15, 501)).as("삭제된 GROUP 채팅은 백필하지 않는다").isZero();
        assertThat(countMembership(11, 103)).as("삭제된 room membership은 백필하지 않는다").isZero();

        assertThat(countMembership(11, 102)).isOne();
        assertThat(joinedAt(11, 102)).isEqualTo(LocalDateTime.of(2026, 9, 2, 9, 10));
        assertThat(leftAt(11, 102)).isEqualTo(LocalDateTime.of(2026, 9, 2, 10, 0));
        assertThat(lastReadMessageId(11, 102)).isEqualTo(77L);

        execute(UP_SQL);

        assertThat(countMembership(11, 101)).isOne();
        assertThat(countMembership(12, 201)).isOne();
        assertThat(countMembership(11, 102)).isOne();
    }

    private static long countMembership(long chatRoomId, long userId) throws SQLException {
        return queryLong("""
                SELECT COUNT(*) FROM chat_room_members
                WHERE chat_room_id = %d AND user_id = %d
                """.formatted(chatRoomId, userId));
    }

    private static LocalDateTime joinedAt(long chatRoomId, long userId) throws SQLException {
        return queryTimestamp("joined_at", chatRoomId, userId);
    }

    private static LocalDateTime leftAt(long chatRoomId, long userId) throws SQLException {
        return queryTimestamp("left_at", chatRoomId, userId);
    }

    private static Long lastReadMessageId(long chatRoomId, long userId) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT last_read_message_id FROM chat_room_members
                     WHERE chat_room_id = %d AND user_id = %d
                     """.formatted(chatRoomId, userId))) {
            return resultSet.next() ? resultSet.getObject(1, Long.class) : null;
        }
    }

    private static LocalDateTime queryTimestamp(String column, long chatRoomId, long userId)
            throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT %s FROM chat_room_members
                     WHERE chat_room_id = %d AND user_id = %d
                     """.formatted(column, chatRoomId, userId))) {
            return resultSet.next() && resultSet.getTimestamp(1) != null
                    ? resultSet.getTimestamp(1).toLocalDateTime()
                    : null;
        }
    }

    private static long queryLong(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
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
