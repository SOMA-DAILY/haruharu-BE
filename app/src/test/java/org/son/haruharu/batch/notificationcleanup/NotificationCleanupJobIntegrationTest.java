package org.son.haruharu.batch.notificationcleanup;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.notification.storage.NotificationHistoryJpaRepository;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 알림함 정리 배치 잡 end-to-end(#357). BATCH_* 스키마 생성 + 잡 실행 + 삭제 동작 + JDBC 영속 + 멱등(같은 버킷 재실행 거부)을
 * 실제 JobOperator/JDBC JobRepository로 검증한다. 30일 지난 행은 created_at 이 감사컬럼이라 JdbcTemplate 로 과거 시각으로 심는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationCleanupJobIntegrationTest {

    @Autowired JobOperator jobOperator;
    @Autowired Job notificationCleanupJob;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired NotificationHistoryJpaRepository notificationHistoryJpaRepository;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM notification_history");
    }

    private void insertNotification(LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO notification_history (user_id, type, title, body, is_read, created_at, updated_at)
                VALUES (?, 'CHAT', 't', 'b', false, ?, ?)""",
                1L, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
    }

    private JobParameters bucket(String value) {
        return new JobParametersBuilder().addString("bucket", value).toJobParameters();
    }

    @Test
    @DisplayName("잡을 실행하면 30일 지난 알림만 삭제되고 최근 것은 남으며, 실행 이력이 BATCH_* 테이블에 영속된다")
    void givenOldAndRecent_whenRunJob_thenDeletesOnlyOldAndPersistsToJdbcRepository() throws Exception {
        insertNotification(LocalDateTime.now().minusDays(40)); // 삭제 대상
        insertNotification(LocalDateTime.now().minusDays(1));  // 유지
        assertThat(notificationHistoryJpaRepository.count()).isEqualTo(2);

        var execution = jobOperator.start(notificationCleanupJob, bucket("delete-case"));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(notificationHistoryJpaRepository.count()).isEqualTo(1); // 최근 것만 남음

        // JDBC JobRepository 증명: 잡 인스턴스가 인메모리(Resourceless)가 아니라 실제 BATCH_* 테이블에 영속됐는지 확인.
        // Resourceless였다면 이 테이블 자체가 없어 쿼리가 SQL 예외로 실패한다. (컨텍스트 공유로 다른 테스트의
        // 인스턴스도 누적되므로 정확한 수치 대신 '기록됨'만 검증한다.)
        Integer instances = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_INSTANCE WHERE JOB_NAME = ?", Integer.class, "notificationCleanupJob");
        assertThat(instances).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("같은 버킷으로 다시 실행하면 JobInstanceAlreadyComplete로 거부된다(멱등·단일 실행)")
    void givenSameBucket_whenRunTwice_thenSecondRejected() throws Exception {
        var first = jobOperator.start(notificationCleanupJob, bucket("2026-06-24"));
        assertThat(first.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // 다른 인스턴스가 같은 버킷을 다시 launch한 상황 — 공유 JobRepository의 (JOB_NAME, JOB_KEY) 유니크가 거부
        assertThatThrownBy(() -> jobOperator.start(notificationCleanupJob, bucket("2026-06-24")))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }
}
