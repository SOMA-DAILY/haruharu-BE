package org.son.haruharu.batch.missionassignment;

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
        prefix = "haruharu.mission.assignment",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MissionAssignmentJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(MissionAssignmentJobLauncher.class);

    private final JobOperator jobOperator;
    private final Job missionAssignmentJob;
    private final Clock clock;

    public MissionAssignmentJobLauncher(JobOperator jobOperator, Job missionAssignmentJob, Clock clock) {
        this.jobOperator = jobOperator;
        this.missionAssignmentJob = missionAssignmentJob;
        this.clock = clock;
    }

    @Scheduled(cron = "${haruharu.mission.assignment.cron:0 * * * * *}", zone = "Asia/Seoul")
    public void launch() {
        String bucket = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).toString();
        JobParameters parameters = new JobParametersBuilder()
                .addString("bucket", bucket)
                .toJobParameters();
        try {
            jobOperator.start(missionAssignmentJob, parameters);
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException alreadyRan) {
            log.atInfo()
                    .addKeyValue("event", "MISSION_ASSIGNMENT_JOB_IDEMPOTENT_SKIP")
                    .addKeyValue("bucket", bucket)
                    .log("미션 배정 잡 멱등 스킵");
        } catch (DataIntegrityViolationException raceLost) {
            log.atInfo()
                    .addKeyValue("event", "MISSION_ASSIGNMENT_JOB_LAUNCH_RACE_SKIP")
                    .addKeyValue("bucket", bucket)
                    .log("미션 배정 잡 동시 launch 레이스 스킵");
        } catch (Exception launchFailure) {
            log.atError()
                    .addKeyValue("event", "MISSION_ASSIGNMENT_JOB_LAUNCH_FAILED")
                    .addKeyValue("bucket", bucket)
                    .setCause(launchFailure)
                    .log("미션 배정 잡 launch 실패");
        }
    }
}
