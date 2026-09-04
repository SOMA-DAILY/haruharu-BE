package org.son.haruharu.batch.api;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.son.haruharu.batch.api.dto.JobExecutionResponse;
import org.son.haruharu.batch.api.dto.JobExecutionStartOutcome;
import org.son.haruharu.batch.api.dto.JobExecutionStartResponse;
import org.son.haruharu.batch.api.exception.BatchJobAlreadyRunningException;
import org.son.haruharu.batch.api.exception.BatchJobExecutionNotFoundException;
import org.son.haruharu.batch.api.exception.BatchJobLaunchRejectedException;
import org.son.haruharu.batch.api.exception.BatchJobNotFoundException;
import org.son.haruharu.batch.api.exception.InvalidBatchJobParametersException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

/**
 * 외부 오케스트레이터용 배치 잡 시작·조회(#389). {@code @Scheduled} 런처와 같은 {@link JobOperator} 경로를 타되,
 * 런처가 "로그 후 삼키던" 멱등 예외를 HTTP 의미로 번역한다.
 * <p>
 * <b>멱등 계약</b>: 같은 {@code bucket} 으로 몇 번을 불러도 잡은 한 번만 돈다. 이미 완료됐으면 기존 실행을 돌려주고
 * ({@code ALREADY_COMPLETED}), 이전 실행이 미완료(FAILED 등)면 Spring Batch 의 재시작 규칙대로 같은 JobInstance 에
 * 새 JobExecution 을 만든다({@code RESTARTED}). 실행 중이면 409. 오케스트레이터의 재시도(at-least-once)가 안전해진다.
 * <p>
 * <b>실행은 동기다</b> — {@code JobOperator} 기본 TaskExecutor 가 요청 스레드에서 잡을 끝까지 돌린 뒤 응답한다. 따라서 응답의
 * status 는 이미 종료 상태(COMPLETED/FAILED)이고, 요청 스레드 점유 시간 = 잡 실행 시간이다. 알림 정리는 삭제 건수에 비례하는
 * 단일 DELETE 라 현재 규모에선 짧지만 상한이 있는 건 아니다. 이 API 는 등록된 모든 Job 을 이름으로 노출하므로, 오래 걸리는 잡
 * (미션 배정·향후 매칭 등)은 비동기 TaskExecutor 로 전환하기 전까지 여기로 트리거하지 않는다 — 전환해도 응답 계약(202 + 폴링)은
 * 그대로다.
 */
@Service
public class BatchJobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(BatchJobExecutionService.class);
    static final String BUCKET_PARAMETER = "bucket";

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Map<String, Job> jobsByName;

    public BatchJobExecutionService(JobOperator jobOperator, JobRepository jobRepository, List<Job> jobs) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        // 빈 이름이 아니라 Job.getName()(= JobInstance 의 JOB_NAME) 으로 색인한다 — API 의 {jobName} 과 BATCH_* 좌표계가 같아야 한다.
        this.jobsByName = jobs.stream().collect(Collectors.toUnmodifiableMap(Job::getName, Function.identity()));
    }

    public JobExecutionStartResponse start(String jobName, String bucket) {
        Job job = jobsByName.get(jobName);
        if (job == null) {
            throw new BatchJobNotFoundException(jobName);
        }
        JobParameters parameters = new JobParametersBuilder()
                .addString(BUCKET_PARAMETER, bucket)
                .toJobParameters();
        // start 전에 마지막 실행을 봐 둔다 — 거부/레이스 시 돌려줄 기존 실행의 폴백. (신규/재시작 판정에는 쓰지 않는다:
        // 이 스냅샷과 start 사이에 다른 호출이 실패 실행을 만들 수 있어, 판정은 start 후 영속 이력으로 한다.)
        JobExecution previous = jobRepository.getLastJobExecution(jobName, parameters);
        try {
            JobExecution execution = jobOperator.start(job, parameters);
            JobExecutionStartOutcome outcome = isRestart(execution)
                    ? JobExecutionStartOutcome.RESTARTED
                    : JobExecutionStartOutcome.STARTED;
            log.atInfo()
                    .addKeyValue("event", "INTERNAL_BATCH_JOB_STARTED")
                    .addKeyValue("jobName", jobName)
                    .addKeyValue("bucket", bucket)
                    .addKeyValue("executionId", execution.getId())
                    .addKeyValue("outcome", outcome.name())
                    .addKeyValue("status", execution.getStatus().name())
                    .log("내부 트리거로 배치 잡 실행");
            return JobExecutionStartResponse.of(execution, bucket, outcome);
        } catch (JobInstanceAlreadyCompleteException notRunnable) {
            // Spring Batch 는 COMPLETED 뿐 아니라 ABANDONED 인스턴스에도 이 예외를 낸다. 완료만 멱등 재호출로 취급하고,
            // ABANDONED 는 수동 개입이 필요한 상태라 거부로 번역한다 — "완료됐다"고 오도하면 안 된다.
            JobExecution latest = latestOrPrevious(jobName, parameters, previous);
            if (latest == null || latest.getStatus() != BatchStatus.COMPLETED) {
                throw new BatchJobLaunchRejectedException(
                        "이전 실행이 " + (latest == null ? "UNKNOWN" : latest.getStatus().name()) + " 상태라 재실행할 수 없다");
            }
            log.atInfo()
                    .addKeyValue("event", "INTERNAL_BATCH_JOB_IDEMPOTENT_SKIP")
                    .addKeyValue("jobName", jobName)
                    .addKeyValue("bucket", bucket)
                    .addKeyValue("executionId", latest.getId())
                    .log("이미 완료된 버킷 — 기존 실행 반환");
            return JobExecutionStartResponse.of(latest, bucket, JobExecutionStartOutcome.ALREADY_COMPLETED);
        } catch (JobExecutionAlreadyRunningException running) {
            JobExecution current = latestOrPrevious(jobName, parameters, previous);
            if (current == null) {
                throw new BatchJobLaunchRejectedException("실행 중으로 거부됐지만 실행을 찾지 못함 — 잠시 후 같은 bucket 으로 재시도");
            }
            throw new BatchJobAlreadyRunningException(current.getId());
        } catch (JobRestartException rejected) {
            throw new BatchJobLaunchRejectedException(rejected.getMessage());
        } catch (InvalidJobParametersException invalid) {
            throw new InvalidBatchJobParametersException(invalid.getMessage());
        } catch (DataIntegrityViolationException | ConcurrencyFailureException race) {
            // 신규 버킷 동시 launch 레이스 — previous == null 판정과 start 사이에 다른 요청/인스턴스가 먼저 JobInstance 를
            // 만들면 (JOB_NAME, JOB_KEY) 유니크 위반(또는 SERIALIZABLE 직렬화 충돌)으로 진다. 런처(NotificationCleanupJobLauncher)와
            // 같은 정상 멱등 경로다. 승자의 실행을 다시 조회해 완료됐으면 ALREADY_COMPLETED, 아니면 409 로 번역한다.
            JobExecution winner = latestOrPrevious(jobName, parameters, previous);
            if (winner == null) {
                // 승자가 롤백됐거나 아직 보이지 않는다 — 폴링할 executionId 가 없으니 "재시도하라"고 정직하게 답한다.
                throw new BatchJobLaunchRejectedException("동시 launch 충돌 후 실행을 찾지 못함 — 잠시 후 같은 bucket 으로 재시도");
            }
            if (winner.getStatus() == BatchStatus.COMPLETED) {
                log.atInfo()
                        .addKeyValue("event", "INTERNAL_BATCH_JOB_LAUNCH_RACE_SKIP")
                        .addKeyValue("jobName", jobName)
                        .addKeyValue("bucket", bucket)
                        .addKeyValue("executionId", winner.getId())
                        .log("동시 launch 레이스 — 승자 실행이 이미 완료, 기존 실행 반환");
                return JobExecutionStartResponse.of(winner, bucket, JobExecutionStartOutcome.ALREADY_COMPLETED);
            }
            if (winner.getStatus().isRunning()) {
                throw new BatchJobAlreadyRunningException(winner.getId());
            }
            // 승자가 이미 FAILED/STOPPED 등 종료 상태 — "실행 중"이 아니라 재시작 대상이다. 재시도하면 RESTARTED 경로를 탄다.
            throw new BatchJobLaunchRejectedException(
                    "동시 launch 충돌 — 승자 실행(execution_id=" + winner.getId() + ")이 " + winner.getStatus().name()
                            + " 상태, 같은 bucket 으로 재시도하면 재시작된다");
        }
    }

    /**
     * 같은 JobInstance 에 <b>내 실행보다 앞선</b> 실행이 있으면 재시작이다. 개수(size &gt; 1)로 판정하면 내 실행 직후
     * 다른 호출이 만든 후속 실행까지 세어 첫 실행을 RESTARTED 로 오분류할 수 있어, ID 순서로 앞선 것만 센다.
     */
    private boolean isRestart(JobExecution execution) {
        List<JobExecution> history = jobRepository.getJobExecutions(execution.getJobInstance());
        return history != null && history.stream().anyMatch(other -> other.getId() < execution.getId());
    }

    public JobExecutionResponse get(long executionId) {
        JobExecution execution;
        try {
            execution = jobRepository.getJobExecution(executionId);
        } catch (EmptyResultDataAccessException missing) {
            // JdbcJobExecutionDao 는 없는 ID 에 null 대신 이 예외를 던진다(JOB_INSTANCE_ID 조회가 0건). 404 로 번역.
            throw new BatchJobExecutionNotFoundException(executionId);
        }
        if (execution == null) {
            throw new BatchJobExecutionNotFoundException(executionId);
        }
        return JobExecutionResponse.from(execution);
    }

    /** 거부 시점에 다시 조회한 최신 실행. 조회가 비면 start 전에 봐 둔 실행으로 대체한다. */
    private JobExecution latestOrPrevious(String jobName, JobParameters parameters, JobExecution previous) {
        JobExecution latest = jobRepository.getLastJobExecution(jobName, parameters);
        return latest != null ? latest : previous;
    }
}
