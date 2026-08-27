package org.son.haruharu.batch.notificationcleanup;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림함 정리 잡 트리거(#357). 배치는 스스로 스케줄하지 않으므로 @Scheduled 가 launch 만 담당한다.
 * <p>
 * <b>멱등/멀티 인스턴스 단일 실행의 핵심</b>: 식별 파라미터 {@code bucket=오늘 날짜}를 넣어 launch 한다. 여러 인스턴스가
 * 같은 날 동시에 launch 해도 JobRepository 의 {@code (job_name, job_key)} 유니크 제약이 하나만 통과시키고, 나머지는
 * {@link JobInstanceAlreadyCompleteException}/{@link JobExecutionAlreadyRunningException} 으로 거부된다 → 잡은 하루 한 번만 실행된다.
 * <p>
 * 지금은 app 인스턴스가 트리거하지만, 나중에 배치 전용 서버로 이 런처를 옮기면 된다(잡 코드 무변경, 같은 RDS JobRepository).
 */
@Component
public class NotificationCleanupJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupJobLauncher.class);

    private final JobOperator jobOperator;
    private final Job notificationCleanupJob;
    private final Clock clock;

    public NotificationCleanupJobLauncher(JobOperator jobOperator, Job notificationCleanupJob, Clock clock) {
        this.jobOperator = jobOperator;
        this.notificationCleanupJob = notificationCleanupJob;
        this.clock = clock;
    }

    /** 매일 새벽 4시. 오늘 버킷으로 launch — 다른 인스턴스가 이미 오늘분을 돌렸으면 멱등 스킵. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void launch() {
        JobParameters parameters = new JobParametersBuilder()
                .addString("bucket", LocalDate.now(clock).toString()) // 일 버킷 = 멱등 키
                .toJobParameters();
        try {
            jobOperator.start(notificationCleanupJob, parameters);
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException alreadyRan) {
            // 다른 인스턴스가 오늘 버킷을 이미 실행/실행 중 — 정상적인 멱등 스킵.
            log.info("알림함 정리 이미 실행됨(멱등 스킵): {}", alreadyRan.getMessage());
        } catch (DataIntegrityViolationException raceLost) {
            // 여러 인스턴스가 같은 밀리초에 launch → BATCH_JOB_INSTANCE (JOB_NAME, JOB_KEY) 유니크 위반으로
            // 한쪽만 통과. 진 쪽도 중복 실행이 아니므로 error 가 아닌 멱등 스킵으로 처리한다.
            log.info("알림함 정리 동시 launch 레이스에서 스킵(멱등): {}", raceLost.getMessage());
        } catch (Exception e) {
            // 파라미터/재시작 오류 등 — 다음 주기에 재시도되므로 로깅만 하고 삼킨다(스케줄러 스레드 보호).
            log.error("알림함 정리 잡 launch 실패", e);
        }
    }
}
