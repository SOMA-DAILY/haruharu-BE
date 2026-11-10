package org.son.haruharu.domain.replyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * 558 적용 뒤 Depth 라벨 SQL(#559)을 두 번 적용·확인하고 되돌린다. 미관측과 0 의 구분은 제약으로도 막는다.
 * 기수 칸(cohort_id) 이전에 적용한 DB 에 다시 적용하면 칸만 더한다.
 */
@Testcontainers
class ReplyDepthMigrationPostgresTest {

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

    private static void assertVerify(Statement s, int expected) throws Exception {
        try (var rows = s.executeQuery(script("559-reply-depth-verify"))) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt("depth_table")).isEqualTo(expected);
            assertThat(rows.getInt("depth_video_index")).isEqualTo(expected);
            assertThat(rows.getInt("depth_status_check")).isEqualTo(expected);
            assertThat(rows.getInt("auxiliary_columns")).isEqualTo(expected * 2);
            assertThat(rows.getInt("cohort_column")).isEqualTo(expected);
        }
    }

    private static void assertVerifyCohort(Statement s, int expected) throws Exception {
        try (var rows = s.executeQuery(script("559-reply-depth-verify"))) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt("cohort_column")).isEqualTo(expected);
        }
    }

    private static String row(long messageId, String status, String depth, String width, String reason) {
        return row(messageId, status, depth, width, reason, "NULL", "NULL");
    }

    private static String row(long messageId, String status, String depth, String width, String reason,
                              String ownerReplied, String followup) {
        return ("INSERT INTO ai_reply.reply_depth_observation (message_id, video_id, viewer_id, room_id, width, depth,"
                + " status, unobserved_reason, window_start, window_end, observed_at, provenance, owner_replied,"
                + " followup_message_count) VALUES (%d, 1, 1, 1, %s, %s, '%s', %s, now() - interval '25 hours',"
                + " now() - interval '1 hour', now(), 'QA', %s, %s)")
                .formatted(messageId, width, depth, status, reason, ownerReplied, followup);
    }

    @Test
    @DisplayName("558 뒤 두 번 적용해도 같고, 미관측은 depth NULL 만, 되돌리면 이 표만 사라진다")
    void given558_whenUpTwiceVerifyAndDown_thenIdempotentAndReversible() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute(script("558-reply-recommendations-up"));
            s.execute(script("559-reply-depth-up"));
            s.execute(script("559-reply-depth-up"));
            assertVerify(s, 1);

            assertThat(s.executeUpdate(row(1, "OBSERVED", "3", "1", "NULL"))).isEqualTo(1);
            assertThat(s.executeUpdate(row(2, "OBSERVED", "0", "0", "NULL"))).isEqualTo(1);
            assertThat(s.executeUpdate(row(3, "UNOBSERVED", "NULL", "NULL", "'CHAIN_BROKEN'"))).isEqualTo(1);
            assertThatThrownBy(() -> s.executeUpdate(row(4, "UNOBSERVED", "0", "0", "'CHAIN_BROKEN'")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(row(5, "OBSERVED", "NULL", "NULL", "NULL")))
                    .isInstanceOf(SQLException.class);
            // 보조 지표: 관측이면 값을 담고, 미관측이면 작성자 답글 여부는 NULL 만, 메시지 수는 음수가 아니다.
            assertThat(s.executeUpdate(row(6, "OBSERVED", "2", "1", "NULL", "true", "4"))).isEqualTo(1);
            assertThat(s.executeUpdate(row(7, "UNOBSERVED", "NULL", "NULL", "'CHAIN_BROKEN'", "NULL", "3")))
                    .isEqualTo(1);
            assertThatThrownBy(() -> s.executeUpdate(row(8, "UNOBSERVED", "NULL", "NULL", "'CHAIN_BROKEN'", "false", "0")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate(row(9, "OBSERVED", "0", "0", "NULL", "false", "-1")))
                    .isInstanceOf(SQLException.class);
            // 기수 칸: 모르면 NULL 로 넣을 수 있다.
            assertThat(s.executeUpdate("UPDATE ai_reply.reply_depth_observation SET cohort_id = 7 WHERE message_id = 1"))
                    .isEqualTo(1);

            // 기수 칸 이전 559 를 적용한 DB: 다시 적용하면 기존 행은 그대로 두고 칸만 더한다(기존 행은 NULL).
            s.execute("ALTER TABLE ai_reply.reply_depth_observation DROP COLUMN cohort_id");
            assertVerifyCohort(s, 0);
            s.execute(script("559-reply-depth-up"));
            assertVerify(s, 1);
            try (var rows = s.executeQuery("SELECT count(*) AS n, count(cohort_id) AS known"
                    + " FROM ai_reply.reply_depth_observation")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("n")).isEqualTo(5);
                assertThat(rows.getInt("known")).isZero();
            }

            s.execute(script("559-reply-depth-down"));
            assertVerify(s, 0);
            try (var rows = s.executeQuery(script("558-reply-recommendations-verify"))) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("ai_reply_tables")).isEqualTo(27);
            }
            s.execute(script("559-reply-depth-down"));
        }
    }
}
