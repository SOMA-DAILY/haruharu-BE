package org.son.haruharu.domain.video;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 배포 SQL을 캡션이 20자인 PostgreSQL에 적용한다. 이미 100자인 DB에 다시 실행해도 안전해야 한다. */
@Testcontainers
class VideoCaptionMigrationPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String HUNDRED = "가".repeat(100);

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeEach
    void legacySchema() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("""
                    DROP SCHEMA public CASCADE;
                    CREATE SCHEMA public;
                    CREATE TABLE video_posts (id bigint PRIMARY KEY, caption varchar(20));
                    CREATE TABLE video_upload_sessions (id uuid PRIMARY KEY, caption varchar(20));
                    INSERT INTO video_posts VALUES (1, '기존 캡션');
                    INSERT INTO video_upload_sessions VALUES (gen_random_uuid(), '기존 캡션');
                    """);
        }
    }

    private String script(String phase) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        return Files.readString(root.resolve("scripts/sql/475-video-caption-" + phase + ".sql"));
    }

    @Test
    @DisplayName("20자 컬럼을 100자로 넓히고 기존 캡션을 보존하며, 다시 실행해도 결과가 같다")
    void givenTwentyCharacterColumns_whenUpTwice_thenStoresHundredCharacters() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute("UPDATE video_posts SET caption = '" + HUNDRED + "' WHERE id = 1"))
                    .isInstanceOf(SQLException.class);

            s.execute(script("up"));
            s.execute(script("up"));

            try (var rows = s.executeQuery(script("verify"))) {
                int columns = 0;
                while (rows.next()) {
                    assertThat(rows.getInt("character_maximum_length")).isEqualTo(100);
                    columns++;
                }
                assertThat(columns).isEqualTo(2);
            }
            try (var rows = s.executeQuery("SELECT caption FROM video_posts WHERE id = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("caption")).isEqualTo("기존 캡션");
            }
            assertThat(s.executeUpdate("UPDATE video_posts SET caption = '" + HUNDRED + "' WHERE id = 1")).isEqualTo(1);
            assertThat(s.executeUpdate("UPDATE video_upload_sessions SET caption = '" + HUNDRED + "'")).isEqualTo(1);
            assertThatThrownBy(() -> s.execute("UPDATE video_posts SET caption = '" + HUNDRED + "가' WHERE id = 1"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
