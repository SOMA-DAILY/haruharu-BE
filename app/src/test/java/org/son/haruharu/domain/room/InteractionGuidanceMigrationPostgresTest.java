package org.son.haruharu.domain.room;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class InteractionGuidanceMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private Connection connection;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        execute("DROP TABLE IF EXISTS room_interaction_guide_claims, room_interaction_first_actions, room_interaction_experiments, room_members, rooms CASCADE");
        execute("CREATE TABLE rooms (id bigint PRIMARY KEY)");
        execute("CREATE TABLE room_members (id bigint PRIMARY KEY)");
        execute("INSERT INTO rooms VALUES (1)");
        execute("INSERT INTO room_members VALUES (10)");
        script("up");
    }

    @AfterEach void close() throws Exception { connection.close(); }

    @Test
    void givenMigration_whenRunAgainAndDown_thenIdempotentAndReversible() throws Exception {
        script("up");
        script("verify");
        assertThat(countTables()).isEqualTo(3);
        script("down");
        assertThat(countTables()).isZero();
        script("up");
        assertThat(countTables()).isEqualTo(3);
    }

    @Test
    void givenClaimConstraints_whenDuplicateKindOrSession_thenDatabaseRejects() throws Exception {
        execute("INSERT INTO room_interaction_guide_claims VALUES ('one',10,'REACTION',30,'session',current_timestamp)");
        assertThatThrownBy(() -> execute("INSERT INTO room_interaction_guide_claims VALUES ('two',10,'REACTION',31,'another',current_timestamp)"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23505");
        assertThatThrownBy(() -> execute("INSERT INTO room_interaction_guide_claims VALUES ('three',10,'REPLY',30,'session',current_timestamp)"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23505");
        assertThatThrownBy(() -> execute("INSERT INTO room_interaction_guide_claims VALUES ('four',10,'REPLY',30,' ',current_timestamp)"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23514");
    }

    @Test
    void givenAssignmentAndMemberState_whenOrphanOrSecondAssignment_thenDatabaseRejects() throws Exception {
        execute("INSERT INTO room_interaction_experiments VALUES (1,'test','CONTROL',current_timestamp)");
        assertThatThrownBy(() -> execute("INSERT INTO room_interaction_experiments VALUES (1,'changed','TOOLTIP',current_timestamp)"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23505");
        assertThatThrownBy(() -> execute("INSERT INTO room_interaction_first_actions VALUES (999,current_timestamp,null)"))
                .isInstanceOf(SQLException.class).extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23503");
    }

    private int countTables() throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'room_interaction_%'")) {
            result.next();
            return result.getInt(1);
        }
    }

    private void script(String suffix) throws Exception {
        Path path = Path.of("..", "scripts", "sql", "541-interaction-guidance-" + suffix + ".sql");
        execute(Files.readString(path));
    }

    private void execute(String sql) throws Exception {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
