package org.son.haruharu.domain.replyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 배포 SQL(#558)을 빈 PostgreSQL 에 두 번 적용하고 되돌린다. 유효 요청 부분 유니크 인덱스가 중복을 막는지도 본다. */
@Testcontainers
class ReplyRecommendationMigrationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private String script(String phase) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        if (!Files.isDirectory(root.resolve("scripts/sql"))) root = root.getParent();
        return Files.readString(root.resolve("scripts/sql/558-reply-recommendations-" + phase + ".sql"));
    }

    @Test
    @DisplayName("두 번 적용해도 같고, 같은 키의 유효 요청은 하나만, 되돌리면 ai_reply 만 사라진다")
    void givenEmptyDatabase_whenUpTwiceAndDown_thenIdempotentAndReversible() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute(script("up"));
            s.execute(script("up"));
            try (var rows = s.executeQuery(script("verify"))) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("ai_reply_tables")).isEqualTo(27);
                assertThat(rows.getInt("caption_revision_table")).isEqualTo(1);
                assertThat(rows.getInt("partial_unique_indexes")).isEqualTo(2);
            }
            String insert = "INSERT INTO ai_reply.analysis_request (video_id, room_id, author_id, scope_hash,"
                    + " excluded_actor_ids, caption_revision, media_revision, preprocess_version, revision_no, origin,"
                    + " status) VALUES (1, 1, 1, 's', '[]', 'c', 'm', 'p', %d, 'PUBLISH', '%s') ON CONFLICT DO NOTHING";
            assertThat(s.executeUpdate(insert.formatted(1, "SNAPSHOT_PENDING"))).isEqualTo(1);
            assertThat(s.executeUpdate(insert.formatted(2, "SNAPSHOT_PENDING"))).isZero();
            s.execute("UPDATE ai_reply.analysis_request SET status = 'CANCELLED'");
            assertThat(s.executeUpdate(insert.formatted(2, "SNAPSHOT_PENDING"))).isEqualTo(1);

            s.execute(script("down"));
            try (var rows = s.executeQuery(script("verify"))) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("ai_reply_tables")).isZero();
                // 앱 코드가 쓰는 캡션 수정 이력은 되돌리기에서도 남긴다(운영은 시작할 때 스키마를 검증한다).
                assertThat(rows.getInt("caption_revision_table")).isEqualTo(1);
            }
        }
    }
}
