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
 * #540 SQL(540-pair-chat-up/down/verify) — 생성된 이름의 옛 CHECK 교체, 재실행, 쌍 제약, 먼저 생긴 쌍 표의 외래 키 보강,
 * 데이터 있을 때 되돌리기 거절.
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

    /** 운영 DB 모양: Hibernate 가 만든 이름의 type CHECK 가 세 표에 걸려 있다. */
    @BeforeEach
    void legacyDatabase() throws Exception {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        execute("""
            CREATE TABLE rooms(id bigint PRIMARY KEY);
            INSERT INTO rooms VALUES (1), (2);
            CREATE TABLE chat_rooms(id bigint PRIMARY KEY, type varchar(10) NOT NULL, room_id bigint UNIQUE,
                match_result_id bigint UNIQUE, close_at timestamp(6), created_at timestamp(6) NOT NULL,
                updated_at timestamp(6) NOT NULL, deleted_at timestamp(6),
                CONSTRAINT chat_rooms_type_check_generated CHECK (type IN ('GROUP', 'DIRECT')));
            INSERT INTO chat_rooms(id, type, room_id, created_at, updated_at) VALUES (10, 'GROUP', 1, now(), now());
            CREATE TABLE room_mission_instances(id bigint PRIMARY KEY, room_id bigint NOT NULL, type varchar(30) NOT NULL,
                details jsonb NOT NULL, CONSTRAINT legacy_generated_type_check CHECK (type IN ('ANNOUNCEMENT', 'PROFILE_REVEAL', 'RECORDING')));
            INSERT INTO room_mission_instances VALUES (100, 1, 'RECORDING', '{"content":"점심"}');
            CREATE TABLE notification_history(id bigint PRIMARY KEY, type varchar(40) NOT NULL,
                CONSTRAINT notification_history_type_check CHECK (type IN ('CHAT', 'CHAT_REPLY', 'ADMIN_MESSAGE')));
            INSERT INTO notification_history VALUES (1000, 'CHAT');
            """);
    }

    long foreignKeysTo(String referenced) throws Exception {
        return count("SELECT count(*) FROM pg_constraint WHERE conrelid='chat_room_pairs'::regclass AND contype='f' "
                + "AND confrelid='" + referenced + "'::regclass");
    }

    long typeChecks(String table) throws Exception {
        return count("SELECT count(*) FROM pg_constraint c JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attname='type' "
                + "WHERE c.conrelid='" + table + "'::regclass AND c.contype='c' AND a.attnum=ANY(c.conkey)");
    }

    @Test
    @DisplayName("up 은 세 표의 옛 type CHECK 를 바꾸고 쌍 표를 만들며, 두 번 돌려도 같다")
    void givenLegacySchema_whenUpTwice_thenAllowsPairValuesAndCreatesPairTable() throws Exception {
        assertThatThrownBy(() -> execute("INSERT INTO chat_rooms(id, type, created_at, updated_at) VALUES (20, 'PAIR', now(), now())"))
                .isInstanceOf(SQLException.class).hasMessageContaining("chat_rooms_type_check_generated");

        script("540-pair-chat-up.sql");
        script("540-pair-chat-up.sql");
        script("540-pair-chat-verify.sql");

        assertThat(foreignKeysTo("chat_rooms")).isEqualTo(1);
        assertThat(foreignKeysTo("rooms")).isEqualTo(1);
        // 방별 조회는 유일 제약(room_id 가 앞 칸)의 색인으로 충분하다. 기본 키·유일 제약 색인만 있다.
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename='chat_room_pairs'")).isEqualTo(2);
        execute("INSERT INTO chat_rooms(id, type, created_at, updated_at) VALUES (20, 'PAIR', now(), now())");
        execute("INSERT INTO chat_room_pairs VALUES (20, 1, 5, 7, now())");
        execute("INSERT INTO room_mission_instances VALUES (101, 1, 'PAIR_CHAT', '{}')");
        execute("INSERT INTO notification_history VALUES (1001, 'PAIR_CHAT')");
        assertThat(typeChecks("chat_rooms")).isEqualTo(1);
        assertThat(typeChecks("room_mission_instances")).isEqualTo(1);
        assertThat(typeChecks("notification_history")).isEqualTo(1);
        // 기존 행은 그대로다.
        assertThat(count("SELECT count(*) FROM chat_rooms WHERE type='GROUP' AND room_id=1")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE type='RECORDING'")).isEqualTo(1);

        // 같은 방의 같은 두 사람은 한 행, 작은 id 가 low, 없는 채팅방·방은 거절.
        execute("INSERT INTO chat_rooms(id, type, created_at, updated_at) VALUES (21, 'PAIR', now(), now())");
        assertThatThrownBy(() -> execute("INSERT INTO chat_room_pairs VALUES (21, 1, 5, 7, now())"))
                .isInstanceOf(SQLException.class).hasMessageContaining("uq_chat_room_pairs_room_users");
        assertThatThrownBy(() -> execute("INSERT INTO chat_room_pairs VALUES (21, 1, 7, 5, now())"))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_chat_room_pairs_order");
        assertThatThrownBy(() -> execute("INSERT INTO chat_room_pairs VALUES (99, 1, 1, 2, now())"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("INSERT INTO chat_room_pairs VALUES (21, 9, 1, 2, now())"))
                .isInstanceOf(SQLException.class);
        // 허용하지 않는 값은 여전히 막는다.
        assertThatThrownBy(() -> execute("UPDATE chat_rooms SET type='SECRET' WHERE id=21"))
                .isInstanceOf(SQLException.class).hasMessageContaining("chat_rooms_type_check");
        assertThatThrownBy(() -> execute("INSERT INTO room_mission_instances VALUES (102, 1, 'SELECTION', '{}')"))
                .isInstanceOf(SQLException.class).hasMessageContaining("room_mission_instances_type_check");
    }

    @Test
    @DisplayName("Hibernate 가 먼저 만든(외래 키 없는) 쌍 표: verify 는 멈추고, up 은 두 외래 키를 한 번씩만 붙인다")
    void givenPairTableCreatedWithoutForeignKeys_whenUpTwice_thenAddsBothForeignKeysOnce() throws Exception {
        // dev(ddl-auto=update) 모양: 엔티티에 연관이 없어 기본 키·유일 제약·CHECK 만 있고 외래 키가 없다.
        execute("""
            CREATE TABLE chat_room_pairs (chat_room_id bigint NOT NULL PRIMARY KEY, room_id bigint NOT NULL,
                low_user_id bigint NOT NULL, high_user_id bigint NOT NULL, created_at timestamp(6) NOT NULL,
                CONSTRAINT uq_chat_room_pairs_room_users UNIQUE (room_id, low_user_id, high_user_id),
                CONSTRAINT ck_chat_room_pairs_order CHECK (low_user_id < high_user_id))
            """);
        assertThatThrownBy(() -> script("540-pair-chat-verify.sql"))
                .isInstanceOf(SQLException.class).hasMessageContaining("foreign keys missing");

        script("540-pair-chat-up.sql");
        script("540-pair-chat-up.sql");

        assertThat(foreignKeysTo("chat_rooms")).isEqualTo(1);
        assertThat(foreignKeysTo("rooms")).isEqualTo(1);
        script("540-pair-chat-verify.sql");
        execute("INSERT INTO chat_rooms(id, type, created_at, updated_at) VALUES (20, 'PAIR', now(), now())");
        execute("INSERT INTO chat_room_pairs VALUES (20, 1, 5, 7, now())");
        assertThatThrownBy(() -> execute("INSERT INTO chat_room_pairs VALUES (99, 1, 1, 2, now())"))
                .isInstanceOf(SQLException.class).hasMessageContaining("fk_chat_room_pairs_chat_room");
        execute("INSERT INTO chat_rooms(id, type, created_at, updated_at) VALUES (21, 'PAIR', now(), now())");
        assertThatThrownBy(() -> execute("INSERT INTO chat_room_pairs VALUES (21, 9, 1, 2, now())"))
                .isInstanceOf(SQLException.class).hasMessageContaining("fk_chat_room_pairs_room");
    }

    @Test
    @DisplayName("이전 up 이 자동 이름으로 만든 외래 키가 있으면 up 을 다시 돌려도 같은 칸의 외래 키를 더하지 않는다")
    void givenPairTableWithGeneratedForeignKeyNames_whenUp_thenNoDuplicateForeignKeys() throws Exception {
        execute("""
            CREATE TABLE chat_room_pairs (chat_room_id bigint PRIMARY KEY REFERENCES chat_rooms(id),
                room_id bigint NOT NULL REFERENCES rooms(id), low_user_id bigint NOT NULL, high_user_id bigint NOT NULL,
                created_at timestamp(6) NOT NULL,
                CONSTRAINT ck_chat_room_pairs_order CHECK (low_user_id < high_user_id),
                CONSTRAINT uq_chat_room_pairs_room_users UNIQUE (room_id, low_user_id, high_user_id))
            """);

        script("540-pair-chat-up.sql");

        assertThat(foreignKeysTo("chat_rooms")).isEqualTo(1);
        assertThat(foreignKeysTo("rooms")).isEqualTo(1);
        script("540-pair-chat-verify.sql");
    }

    @Test
    @DisplayName("down 은 1:1 대화 데이터가 있으면 멈추고, 없으면 새 값과 쌍 표를 거둔다")
    void givenPairData_whenDown_thenRefusesUntilCleared() throws Exception {
        script("540-pair-chat-up.sql");
        execute("INSERT INTO room_mission_instances VALUES (101, 1, 'PAIR_CHAT', '{}')");

        assertThatThrownBy(() -> script("540-pair-chat-down.sql"))
                .isInstanceOf(SQLException.class).hasMessageContaining("manual reconciliation");
        assertThat(count("SELECT count(*) FROM room_mission_instances WHERE type='PAIR_CHAT'")).isEqualTo(1);

        execute("DELETE FROM room_mission_instances WHERE type='PAIR_CHAT'");
        script("540-pair-chat-down.sql");

        assertThat(count("SELECT count(*) FROM pg_class WHERE relname='chat_room_pairs'")).isZero();
        assertThatThrownBy(() -> execute("INSERT INTO chat_rooms(id, type, created_at, updated_at) VALUES (22, 'PAIR', now(), now())"))
                .isInstanceOf(SQLException.class).hasMessageContaining("chat_rooms_type_check");
        assertThatThrownBy(() -> execute("INSERT INTO room_mission_instances VALUES (103, 1, 'PAIR_CHAT', '{}')"))
                .isInstanceOf(SQLException.class).hasMessageContaining("room_mission_instances_type_check");
        // 되돌린 뒤에도 기존 값은 쓸 수 있다.
        execute("INSERT INTO notification_history VALUES (1002, 'CHAT_REPLY')");
        execute("INSERT INTO room_mission_instances VALUES (104, 1, 'RECORDING', '{\"content\":\"촬영\"}')");
    }
}
