package org.son.haruharu.domain.replyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 558·559 뒤 방 단위 추천 스위치 SQL(#560)을 두 번 적용·확인하고 되돌린다. 기존 방은 false 로 채워진다. */
@Testcontainers
class RoomReplyRecommendationMigrationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String script(String name) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        return Files.readString(root.resolve("scripts/sql/" + name + ".sql"));
    }

    private static int verify(Statement s) throws Exception {
        try (var rows = s.executeQuery(script("560-room-reply-recommendation-verify"))) {
            assertThat(rows.next()).isTrue();
            return rows.getInt("reply_recommendation_column");
        }
    }

    @Test
    @DisplayName("558·559 뒤 두 번 적용해도 같고, 기존 방과 칸을 생략한 새 방은 false, 되돌리면 칸만 사라진다")
    void given558And559_whenUpTwiceVerifyAndDown_thenIdempotentAndReversible() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            // 서비스 표는 Hibernate 가 만든다. 이 SQL 이 건드리는 rooms 의 최소 모양만 둔다.
            s.execute("CREATE TABLE rooms (id BIGSERIAL PRIMARY KEY, status VARCHAR(20) NOT NULL)");
            s.execute("INSERT INTO rooms (status) VALUES ('ACTIVE')");
            s.execute(script("558-reply-recommendations-up"));
            s.execute(script("559-reply-depth-up"));

            s.execute(script("560-room-reply-recommendation-up"));
            s.execute(script("560-room-reply-recommendation-up"));
            assertThat(verify(s)).isEqualTo(1);

            s.execute("INSERT INTO rooms (status) VALUES ('PENDING')");
            try (var rows = s.executeQuery(
                    "SELECT count(*) FROM rooms WHERE reply_recommendation_enabled = false")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            assertThat(s.executeUpdate("UPDATE rooms SET reply_recommendation_enabled = true WHERE id = 1"))
                    .isEqualTo(1);

            s.execute(script("560-room-reply-recommendation-down"));
            assertThat(verify(s)).isZero();
            try (var rows = s.executeQuery("SELECT count(*) FROM rooms")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            try (var rows = s.executeQuery(script("559-reply-depth-verify"))) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("depth_table")).isEqualTo(1);
            }
            s.execute(script("560-room-reply-recommendation-down"));
        }
    }
}
