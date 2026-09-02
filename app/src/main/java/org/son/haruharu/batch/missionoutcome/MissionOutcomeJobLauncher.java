package org.son.haruharu.batch.missionoutcome;

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
        prefix = "haruharu.mission.outcome",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MissionOutcomeJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(MissionOutcomeJobLauncher.class);

    private final JobOperator jobOperator;
    private final Job missionOutcomeJob;
    private final Clock clock;

    public MissionOutcomeJobLauncher(JobOperator jobOperator, Job missionOutcomeJob, Clock clock) {
        this.jobOperator = jobOperator;
        this.missionOutcomeJob = missionOutcomeJob;
        this.clock = clock;
    }

    @Scheduled(cron = "${haruharu.mission.outcome.cron:30 * * * * *}", zone = "Asia/Seoul")
    public void launch() {
        String bucket = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).toString();
        JobParameters parameters = new JobParametersBuilder()
                .addString("bucket", bucket)
                .toJobParameters();
        try {
            jobOperator.start(missionOutcomeJob, parameters);
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException alreadyRan) {
            log.atInfo()
                    .addKeyValue("event", "MISSION_OUTCOME_JOB_IDEMPOTENT_SKIP")
                    .addKeyValue("bucket", bucket)
                    .log("미션 outcome 잡 멱등 스킵");
        } catch (DataIntegrityViolationException | ConcurrencyFailureException raceLost) {
            // JobRepository 는 SERIALIZABLE 이라 다른 잡과 동시에 launch 하면 직렬화 충돌이 난다.
            // cron 을 어긋나게 두는 게 1차 방어이고, 이 catch 는 그래도 겹쳤을 때 다음 주기로 넘기는 2차 방어다.
            log.atInfo()
                    .addKeyValue("event", "MISSION_OUTCOME_JOB_LAUNCH_RACE_SKIP")
                    .addKeyValue("bucket", bucket)
                    .addKeyValue("errorType", raceLost.getClass().getSimpleName())
                    .log("미션 outcome 잡 동시 launch 레이스 스킵");
        } catch (Exception launchFailure) {
            log.atError()
                    .addKeyValue("event", "MISSION_OUTCOME_JOB_LAUNCH_FAILED")
                    .addKeyValue("bucket", bucket)
                    .setCause(launchFailure)
                    .log("미션 outcome 잡 launch 실패");
        }
    }
}
