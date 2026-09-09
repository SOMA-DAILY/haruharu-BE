package org.son.haruharu.batch.notificationcleanup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시나리오 8(#389): 스케줄러 토글을 끄면 알림 정리 @Scheduled 런처가 등록되지 않는다.
 * 잡 자체(Job 빈)는 남아 있어야 한다 — Airflow 가 트리거 API 로 부르기 때문이다.
 */
@SpringBootTest(properties = "haruharu.batch.notification-cleanup.scheduler.enabled=false")
@ActiveProfiles("test")
class NotificationCleanupSchedulerToggleTest {

    @Autowired ApplicationContext applicationContext;
    @Autowired Job notificationCleanupJob;

    @Test
    @DisplayName("scheduler.enabled=false 면 런처 빈이 없고 Job 빈은 남는다")
    void givenSchedulerDisabled_whenContextLoads_thenNoLauncherButJobRemains() {
        assertThat(applicationContext.getBeanNamesForType(NotificationCleanupJobLauncher.class)).isEmpty();
        assertThat(notificationCleanupJob.getName()).isEqualTo(NotificationCleanupJobConfig.JOB_NAME);
    }
}
