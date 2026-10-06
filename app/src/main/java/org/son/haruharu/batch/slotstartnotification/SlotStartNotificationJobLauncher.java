package org.son.haruharu.batch.slotstartnotification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(
        prefix = "haruharu.notification.slot-start",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SlotStartNotificationJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(SlotStartNotificationJobLauncher.class);

    private final JobOperator jobOperator;
    private final Job slotStartNotificationJob;
    private final Clock clock;

    public SlotStartNotificationJobLauncher(JobOperator jobOperator, Job slotStartNotificationJob, Clock clock) {
        this.jobOperator = jobOperator;
        this.slotStartNotificationJob = slotStartNotificationJob;
        this.clock = clock;
    }

    // 다른 배치와 동시에 실행되면 Batch 메타 테이블 직렬화 문제가 발생할 수 있다.
    // 15초 늦게 시작하더라도 반올림으로 인해 정각 시각은 같다.
    @Scheduled(cron = "${haruharu.notification.slot-start.cron:15 0 * * * *}", zone = "Asia/Seoul")
    public void launch() {
        // 정각 시각
        LocalDateTime slotAt = LocalDateTime.now(clock).plusMinutes(30).truncatedTo(ChronoUnit.HOURS);
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("bucket", slotAt.toString())   // "2026-09-19T23:00" = 시간 버킷(멱등 키)
                .toJobParameters();
        try {
            jobOperator.start(slotStartNotificationJob, jobParameters);
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException alreadyRan) {
            // 다른 인스턴스가 오늘 버킷을 이미 실행/실행 중
            log.info("슬롯 시작 알림 잡 이미 실행됨(멱등 스킵): bucket={}", slotAt);
        } catch (DataIntegrityViolationException raceLost) {
            // 여러 인스턴스가 같은 밀리초에 launch → BATCH_JOB_INSTANCE (JOB_NAME, JOB_KEY) 유니크 위반
            log.info("슬롯 시작 알림 잡 동시 launch 레이스 스킵(멱등): bucket={}", slotAt);
        } catch (Exception e) {
            // 파라미터/재시작 오류 등
            log.error("슬롯 시작 알림 잡 launch 실패: bucket={}", slotAt, e);
        }
    }
}
