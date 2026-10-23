package org.son.haruharu.domain.chat;

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

/**
 * #540 SQL(540-pair-chat-up/down/verify) — 생성된 이름의 옛 CHECK 교체, room_id 전체 유니크를 GROUP 부분 유니크로 교체,
 * 재실행, 데이터 있을 때 되돌리기 거절.
 */
@Testcontainers
class PairChatMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    Connection connection() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    void execute(String sql) throws Exception {
        try (var c = connection(); var s = c.createStatement()) { s.execute(sql); }
    }

    long count(String sql) throws Exception {
        try (var c = connection(); var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getLong(1); }
    }

    void script(String name) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("Missing migration scripts");
        execute(Files.readString(root.resolve("scripts/sql/" + name)));
    }

    /** 운영 DB 모양: Hibernate 가 만든 이름의 type CHECK 가 세 표에 걸려 있고 room_id 는 전체 유니크다. */
    void legacyDatabase(String roomIdUnique) throws Exception {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        execute("""
            CREATE TABLE rooms(id bigint PRIMARY KEY);
            INSERT INTO rooms VALUES (1), (2);
            CREATE TABLE chat_rooms(id bigint PRIMARY KEY, type varchar(10) NOT NULL, room_id bigint,
                match_result_id bigint, close_at timestamp(6), created_at timestamp(6) NOT NULL,
                updated_at timestamp(6) NOT NULL, deleted_at timestamp(6),
                CONSTRAINT uq_chat_rooms_match_result UNIQUE (match_result_id),
                %s,
                CONSTRAINT chat_rooms_type_check_generated CHECK (type IN ('GROUP', 'DIRECT')));
            INSERT INTO chat_rooms(id, type, room_id, created_at, updated_at) VALUES (10, 'GROUP', 1, now(), now());
            INSERT INTO chat_rooms(id, type, match_result_id, created_at, updated_at) VALUES (11, 'DIRECT', 500, now(), now());
            CREATE TABLE room_mission_instances(id bigint PRIMARY KEY, room_id bigint NOT NULL, type varchar(30) NOT NULL,
                details jsonb NOT NULL, CONSTRAINT legacy_generated_type_check CHECK (type IN ('ANNOUNCEMENT', 'PROFILE_REVEAL', 'RECORDING')));
            INSERT INTO room_mission_instances VALUES (100, 1, 'RECORDING', '{"content":"점심"}');
            CREATE TABLE notification_history(id bigint PRIMARY KEY, type varchar(40) NOT NULL,
                CONSTRAINT notification_history_type_check CHECK (type IN ('CHAT', 'CHAT_REPLY', 'ADMIN_MESSAGE')));
            INSERT INTO notification_history VALUES (1000, 'CHAT');
            """.formatted(roomIdUnique));
    }

    @BeforeEach
    void prodShape() throws Exception {
        legacyDatabase("CONSTRAINT uq_chat_rooms_room UNIQUE (room_id)");
    }

    long typeChecks(String table) throws Exception {
        return count("SELECT count(*) FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='type' "
                + "WHERE c.conrelid='" + table + "'::regclass AND c.contype='c' AND a.attnum=ANY(c.conkey)");
    }

    long roomIdUniqueConstraints() throws Exception {
        return count("SELECT count(*) FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='room_id' "
                + "WHERE c.conrelid='chat_rooms'::regclass AND c.contype='u' AND c.conkey=ARRAY[a.attnum]");
    }

    void insertChat(long id, String type, long roomId) throws Exception {
        execute("INSERT INTO chat_rooms(id, type, room_id, created_at, updated_at) VALUES (" + id + ", '" + type + "', "
                + roomId + ", now(), now())");
    }

    @Test
    @DisplayName("up 은 새 유형 값을 허용하고 room_id 유일을 GROUP 한정으로 바꾸며, 두 번 돌려도 같다")
    void givenProdSchema_whenUpTwice_thenPairSharesRoomIdButGroupStaysOnePerRoom() throws Exception {
        assertThatThrownBy(() -> script("540-pair-chat-verify.sql"))
                .isInstanceOf(SQLException.class).hasMessageContaining("full UNIQUE");
        assertThatThrownBy(() -> insertChat(20, "PAIR", 1))
                .isInstanceOf(SQLException.class).hasMessageContaining("chat_rooms_type_check_generated");

        script("540-pair-chat-up.sql");
        script("540-pair-chat-up.sql");
        script("540-pair-chat-verify.sql");

        assertThat(roomIdUniqueConstraints()).isZero();
        // 같은 방(1)의 GROUP 10 옆에 PAIR 여러 개를 둘 수 있다.
        insertChat(20, "PAIR", 1);
        insertChat(21, "PAIR", 1);
        // GROUP 은 방당 1개 그대로다. 다른 방 GROUP 은 된다.
        assertThatThrownBy(() -> insertChat(22, "GROUP", 1))
                .isInstanceOf(SQLException.class).hasMessageContaining("ux_chat_rooms_group_room");
        insertChat(23, "GROUP", 2);
        execute("INSERT INTO room_mission_instances VALUES (101, 1, 'PAIR_CHAT', '{}')");
        execute("INSERT INTO notification_history VALUES (1001, 'PAIR_CHAT')");
        assertThat(typeChecks("chat_rooms")).isEqualTo(1);
        assertThat(typeChecks("room_mission_instances")).isEqualTo(1);
        assertThat(typeChecks("notification_history")).isEqualTo(1);
        // 기존 행과 DIRECT 유일은 그대로다.
        assertThat(count("SELECT count(*) FROM chat_rooms WHERE type='GROUP' AND room_id=1")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE type='RECORDING'")).isEqualTo(1);
        assertThatThrownBy(() -> execute("INSERT INTO chat_rooms(id, type, match_result_id, created_at, updated_at) "
                + "VALUES (24, 'DIRECT', 500, now(), now())"))
                .isInstanceOf(SQLException.class).hasMessageContaining("uq_chat_rooms_match_result");
        // 허용하지 않는 값은 여전히 막는다.
        assertThatThrownBy(() -> execute("UPDATE chat_rooms SET type='SECRET' WHERE id=21"))
                .isInstanceOf(SQLException.class).hasMessageContaining("chat_rooms_type_check");
        assertThatThrownBy(() -> execute("INSERT INTO room_mission_instances VALUES (102, 1, 'SELECTION', '{}')"))
                .isInstanceOf(SQLException.class).hasMessageContaining("room_mission_instances_type_check");
    }

    @Test
    @DisplayName("room_id 전체 유니크가 자동 이름이어도 up 이 지우고 GROUP 부분 유니크로 바꾼다")
    void givenGeneratedRoomIdUniqueName_whenUp_thenReplaced() throws Exception {
        legacyDatabase("UNIQUE (room_id)");

        script("540-pair-chat-up.sql");
        script("540-pair-chat-verify.sql");

        assertThat(roomIdUniqueConstraints()).isZero();
        insertChat(20, "PAIR", 1);
        assertThatThrownBy(() -> insertChat(22, "GROUP", 1))
                .isInstanceOf(SQLException.class).hasMessageContaining("ux_chat_rooms_group_room");
    }

    @Test
    @DisplayName("down 은 1:1 대화 데이터가 있으면 멈추고, 없으면 새 값을 거두고 room_id 전체 유니크로 되돌린다")
    void givenPairData_whenDown_thenRefusesUntilCleared() throws Exception {
        script("540-pair-chat-up.sql");
        insertChat(20, "PAIR", 1);

        assertThatThrownBy(() -> script("540-pair-chat-down.sql"))
                .isInstanceOf(SQLException.class).hasMessageContaining("manual reconciliation");
        assertThat(count("SELECT count(*) FROM chat_rooms WHERE type='PAIR'")).isEqualTo(1);

        execute("DELETE FROM chat_rooms WHERE type='PAIR'");
        script("540-pair-chat-down.sql");

        assertThat(count("SELECT count(*) FROM pg_class WHERE relname='ux_chat_rooms_group_room'")).isZero();
        assertThat(count("SELECT count(*) FROM pg_constraint WHERE conname='uq_chat_rooms_room'")).isEqualTo(1);
        assertThatThrownBy(() -> insertChat(22, "PAIR", 2))
                .isInstanceOf(SQLException.class).hasMessageContaining("chat_rooms_type_check");
        assertThatThrownBy(() -> execute("INSERT INTO room_mission_instances VALUES (103, 1, 'PAIR_CHAT', '{}')"))
                .isInstanceOf(SQLException.class).hasMessageContaining("room_mission_instances_type_check");
        // 되돌린 뒤에도 기존 값은 쓸 수 있다.
        execute("INSERT INTO notification_history VALUES (1002, 'CHAT_REPLY')");
        execute("INSERT INTO room_mission_instances VALUES (104, 1, 'RECORDING', '{\"content\":\"촬영\"}')");
    }
}
