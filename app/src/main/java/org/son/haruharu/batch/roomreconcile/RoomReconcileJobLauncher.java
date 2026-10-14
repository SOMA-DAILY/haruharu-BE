package org.son.haruharu.batch.roomreconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@ConditionalOnProperty(
        prefix = "haruharu.room.reconcile",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RoomReconcileJobLauncher {
    private static final Logger log = LoggerFactory.getLogger(RoomReconcileJobLauncher.class);

    private final JobOperator jobOperator;
    private final Job roomReconcileJob;
    private final Clock clock;

    public RoomReconcileJobLauncher(JobOperator jobOperator, Job roomReconcileJob, Clock clock) {
        this.jobOperator = jobOperator;
        this.roomReconcileJob = roomReconcileJob;
        this.clock = clock;
    }

    @Scheduled(cron = "${haruharu.room.reconcile.cron:45 */5 * * * *}", zone = "Asia/Seoul")
    public void launch() {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime bucket = now.withMinute(now.getMinute() / 5 * 5);
        JobParameters parameters = new JobParametersBuilder()
                .addString("bucket", bucket.toString())
                .toJobParameters();
        try {
            jobOperator.start(roomReconcileJob, parameters);
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException alreadyRan) {
            log.info("방 status 전이 이미 실행됨(멱등 스킵): {}", alreadyRan.getMessage());
        } catch (DataIntegrityViolationException | ConcurrencyFailureException raceLost) {
            // 다른 인스턴스·내부 배치 API 와 같은 bucket 을 동시에 launch 하면 유니크 위반 또는 SERIALIZABLE 직렬화 충돌로 진다.
            log.info("방 status 전이 동시 launch 레이스에서 스킵(멱등): {}", raceLost.getMessage());
        } catch (Exception e) {
            log.error("방 status 전이 launch 실패", e);
        }
    }
}
