package org.son.haruharu.batch.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.batch.api.dto.JobExecutionResponse;
import org.son.haruharu.batch.api.dto.JobExecutionStartOutcome;
import org.son.haruharu.batch.api.dto.JobExecutionStartResponse;
import org.son.haruharu.batch.api.exception.BatchJobAlreadyRunningException;
import org.son.haruharu.batch.api.exception.BatchJobExecutionNotFoundException;
import org.son.haruharu.batch.api.exception.BatchJobLaunchRejectedException;
import org.son.haruharu.batch.api.exception.InvalidBatchJobParametersException;
import org.son.haruharu.batch.api.exception.BatchJobNotFoundException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 내부 배치 트리거 서비스 단위 테스트(#389). JobOperator/JobRepository 를 목으로 두고
 * start 의 결과 분기(신규·재시작·이미 완료·실행 중·미등록)와 조회 매핑을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class BatchJobExecutionServiceTest {

    private static final String JOB_NAME = "notificationCleanupJob";

    @Mock JobOperator jobOperator;
    @Mock JobRepository jobRepository;
    @Mock Job notificationCleanupJob;

    BatchJobExecutionService service;

    @BeforeEach
    void setUp() {
        given(notificationCleanupJob.getName()).willReturn(JOB_NAME);
        service = new BatchJobExecutionService(jobOperator, jobRepository, List.of(notificationCleanupJob));
    }

    private JobParameters bucket(String value) {
        return new JobParametersBuilder().addString("bucket", value).toJobParameters();
    }

    private JobExecution execution(long id, String bucketValue, BatchStatus status) {
        JobExecution execution = new JobExecution(id, new JobInstance(id, JOB_NAME), bucket(bucketValue));
        execution.setStatus(status);
        execution.setExitStatus(new ExitStatus(status.name()));
        return execution;
    }

    @Test
    @DisplayName("처음 보는 버킷이면 잡을 시작하고 STARTED outcome 을 돌려준다")
    void givenNewBucket_whenStart_thenStartedOutcome() throws Exception {
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-04"))).willReturn(null);
        JobExecution created = execution(1L, "2026-09-04", BatchStatus.COMPLETED);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class))).willReturn(created);
        given(jobRepository.getJobExecutions(created.getJobInstance())).willReturn(List.of(created));

        JobExecutionStartResponse response = service.start(JOB_NAME, "2026-09-04");

        assertThat(response.executionId()).isEqualTo(1L);
        assertThat(response.jobName()).isEqualTo(JOB_NAME);
        assertThat(response.bucket()).isEqualTo("2026-09-04");
        assertThat(response.outcome()).isEqualTo(JobExecutionStartOutcome.STARTED);
    }

    @Test
    @DisplayName("이전 실행이 FAILED 인 버킷을 다시 시작하면 RESTARTED outcome 을 돌려준다")
    void givenFailedPrevious_whenStart_thenRestartedOutcome() throws Exception {
        JobExecution failed = execution(7L, "2026-09-03", BatchStatus.FAILED);
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-03"))).willReturn(failed);
        JobExecution restarted = new JobExecution(8L, failed.getJobInstance(), bucket("2026-09-03"));
        restarted.setStatus(BatchStatus.COMPLETED);
        restarted.setExitStatus(ExitStatus.COMPLETED);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class))).willReturn(restarted);
        given(jobRepository.getJobExecutions(failed.getJobInstance())).willReturn(List.of(failed, restarted));

        JobExecutionStartResponse response = service.start(JOB_NAME, "2026-09-03");

        assertThat(response.executionId()).isEqualTo(8L);
        assertThat(response.outcome()).isEqualTo(JobExecutionStartOutcome.RESTARTED);
    }

    @Test
    @DisplayName("start 전 스냅샷이 비어 있어도(레이스) 영속 이력에 실행이 2개면 RESTARTED 로 판정한다")
    void givenStaleEmptySnapshotButHistoryOfTwo_whenStart_thenRestarted() throws Exception {
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-02"))).willReturn(null); // 스냅샷은 비어 있었음
        JobExecution racerFailed = execution(30L, "2026-09-02", BatchStatus.FAILED);
        JobExecution mine = new JobExecution(31L, racerFailed.getJobInstance(), bucket("2026-09-02"));
        mine.setStatus(BatchStatus.COMPLETED);
        mine.setExitStatus(ExitStatus.COMPLETED);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class))).willReturn(mine);
        given(jobRepository.getJobExecutions(racerFailed.getJobInstance())).willReturn(List.of(racerFailed, mine));

        assertThat(service.start(JOB_NAME, "2026-09-02").outcome()).isEqualTo(JobExecutionStartOutcome.RESTARTED);
    }

    @Test
    @DisplayName("이전 실행이 ABANDONED 면 ALREADY_COMPLETED 로 오도하지 않고 BatchJobLaunchRejected(409) 를 던진다")
    void givenAbandonedPrevious_whenStart_thenLaunchRejected() throws Exception {
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-01")))
                .willReturn(execution(40L, "2026-09-01", BatchStatus.ABANDONED));
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new JobInstanceAlreadyCompleteException("abandoned"));

        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-09-01"))
                .isInstanceOf(BatchJobLaunchRejectedException.class)
                .hasMessageContaining("ABANDONED");
    }

    @Test
    @DisplayName("JobRestartException 은 409 BatchJobLaunchRejected, InvalidJobParametersException 은 400 으로 매핑된다")
    void givenFrameworkRejections_whenStart_thenMappedExceptions() throws Exception {
        given(jobRepository.getLastJobExecution(eq(JOB_NAME), any(JobParameters.class))).willReturn(null);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new JobRestartException("not restartable"))
                .willThrow(new InvalidJobParametersException("bad params"));

        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-08-31"))
                .isInstanceOf(BatchJobLaunchRejectedException.class);
        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-08-30"))
                .isInstanceOf(InvalidBatchJobParametersException.class);
    }

    @Test
    @DisplayName("이미 완료된 버킷이면 예외 대신 기존 실행을 ALREADY_COMPLETED 로 돌려준다 (멱등 재호출)")
    void givenCompletedBucket_whenStart_thenAlreadyCompletedWithExistingExecution() throws Exception {
        JobExecution completed = execution(3L, "2026-09-04", BatchStatus.COMPLETED);
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-04"))).willReturn(completed);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new JobInstanceAlreadyCompleteException("already complete"));

        JobExecutionStartResponse response = service.start(JOB_NAME, "2026-09-04");

        assertThat(response.executionId()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.outcome()).isEqualTo(JobExecutionStartOutcome.ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("실행 중인 버킷이면 BatchJobAlreadyRunningException 에 기존 executionId 를 실어 던진다")
    void givenRunningBucket_whenStart_thenThrowsAlreadyRunningWithExecutionId() throws Exception {
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-04")))
                .willReturn(execution(5L, "2026-09-04", BatchStatus.STARTED));
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new JobExecutionAlreadyRunningException("running"));

        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-09-04"))
                .isInstanceOf(BatchJobAlreadyRunningException.class)
                .satisfies(e -> assertThat(((BatchJobAlreadyRunningException) e).getExecutionId()).isEqualTo(5L))
                .hasMessageContaining("execution_id=5");
    }

    @Test
    @DisplayName("신규 버킷 동시 launch 레이스에서 지면(유니크 위반) 승자가 완료됐을 때 ALREADY_COMPLETED 로 기존 실행을 돌려준다")
    void givenRaceLostAndWinnerCompleted_whenStart_thenAlreadyCompleted() throws Exception {
        JobExecution winner = execution(21L, "2026-09-05", BatchStatus.COMPLETED);
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-05"))).willReturn(null, winner);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new DataIntegrityViolationException("JOB_INST_UN"));

        JobExecutionStartResponse response = service.start(JOB_NAME, "2026-09-05");

        assertThat(response.executionId()).isEqualTo(21L);
        assertThat(response.outcome()).isEqualTo(JobExecutionStartOutcome.ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("신규 버킷 동시 launch 레이스에서 지고 승자가 아직 실행 중이면 409(BatchJobAlreadyRunning)로 승자 executionId 를 준다")
    void givenRaceLostAndWinnerRunning_whenStart_thenAlreadyRunning() throws Exception {
        JobExecution winner = execution(22L, "2026-09-06", BatchStatus.STARTED);
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-06"))).willReturn(null, winner);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new DataIntegrityViolationException("JOB_INST_UN"));

        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-09-06"))
                .isInstanceOf(BatchJobAlreadyRunningException.class)
                .hasMessageContaining("execution_id=22");
    }

    @Test
    @DisplayName("레이스에서 졌는데 승자 실행이 어디에도 없으면 execution_id=unknown 대신 409 LaunchRejected(재시도 요청)로 답한다")
    void givenRaceLostAndNoWinnerVisible_whenStart_thenLaunchRejected() throws Exception {
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-07"))).willReturn(null, (JobExecution) null);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new DataIntegrityViolationException("JOB_INST_UN"));

        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-09-07"))
                .isInstanceOf(BatchJobLaunchRejectedException.class)
                .hasMessageContaining("재시도");
    }

    @Test
    @DisplayName("내 실행 직후 다른 호출이 후속 실행을 만들어 이력이 2개여도, 앞선 실행이 없으면 STARTED 다 (개수가 아닌 순서로 판정)")
    void givenLaterExecutionByRacer_whenStart_thenStillStarted() throws Exception {
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-08"))).willReturn(null);
        JobExecution mine = execution(50L, "2026-09-08", BatchStatus.FAILED);
        JobExecution racerLater = new JobExecution(51L, mine.getJobInstance(), bucket("2026-09-08"));
        racerLater.setStatus(BatchStatus.STARTED);
        racerLater.setExitStatus(ExitStatus.EXECUTING);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class))).willReturn(mine);
        given(jobRepository.getJobExecutions(mine.getJobInstance())).willReturn(List.of(mine, racerLater));

        assertThat(service.start(JOB_NAME, "2026-09-08").outcome()).isEqualTo(JobExecutionStartOutcome.STARTED);
    }

    @Test
    @DisplayName("레이스 승자가 이미 FAILED 로 끝났으면 '실행 중'(409 RUNNING)이 아니라 재시작 안내(409 LAUNCH_REJECTED)를 준다")
    void givenRaceLostAndWinnerFailed_whenStart_thenLaunchRejectedWithRestartHint() throws Exception {
        JobExecution winner = execution(60L, "2026-09-09", BatchStatus.FAILED);
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-09"))).willReturn(null, winner);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new DataIntegrityViolationException("JOB_INST_UN"));

        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-09-09"))
                .isInstanceOf(BatchJobLaunchRejectedException.class)
                .hasMessageContaining("execution_id=60").hasMessageContaining("FAILED");
    }

    @Test
    @DisplayName("실행 중으로 거부됐는데 실행을 못 찾으면 execution_id=unknown 대신 409 LaunchRejected(재시도)로 답한다")
    void givenRunningButNoExecutionVisible_whenStart_thenLaunchRejected() throws Exception {
        given(jobRepository.getLastJobExecution(JOB_NAME, bucket("2026-09-10"))).willReturn(null, (JobExecution) null);
        given(jobOperator.start(eq(notificationCleanupJob), any(JobParameters.class)))
                .willThrow(new JobExecutionAlreadyRunningException("running"));

        assertThatThrownBy(() -> service.start(JOB_NAME, "2026-09-10"))
                .isInstanceOf(BatchJobLaunchRejectedException.class)
                .hasMessageContaining("재시도");
    }

    @Test
    @DisplayName("등록되지 않은 잡 이름이면 BatchJobNotFoundException 을 던지고 JobOperator 를 호출하지 않는다")
    void givenUnknownJob_whenStart_thenThrowsNotFound() throws Exception {
        assertThatThrownBy(() -> service.start("unknownJob", "2026-09-04"))
                .isInstanceOf(BatchJobNotFoundException.class);
        verify(jobOperator, never()).start(any(Job.class), any(JobParameters.class));
    }

    @Test
    @DisplayName("실행 ID 로 조회하면 상태·종료코드·스텝 카운터가 매핑된다")
    void givenExecution_whenGet_thenMapsStatusAndSteps() {
        JobExecution execution = execution(9L, "2026-09-04", BatchStatus.COMPLETED);
        StepExecution step = new StepExecution(11L, "notificationCleanupStep", execution);
        step.setStatus(BatchStatus.COMPLETED);
        step.setReadCount(0);
        step.setWriteCount(2);
        step.setCommitCount(1);
        step.setRollbackCount(0);
        execution.addStepExecutions(List.of(step));
        given(jobRepository.getJobExecution(9L)).willReturn(execution);

        JobExecutionResponse response = service.get(9L);

        assertThat(response.executionId()).isEqualTo(9L);
        assertThat(response.jobName()).isEqualTo(JOB_NAME);
        assertThat(response.bucket()).isEqualTo("2026-09-04");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.exitCode()).isEqualTo("COMPLETED");
        assertThat(response.steps()).hasSize(1);
        assertThat(response.steps().get(0).name()).isEqualTo("notificationCleanupStep");
        assertThat(response.steps().get(0).writeCount()).isEqualTo(2);
        assertThat(response.steps().get(0).commitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 실행 ID 조회는 BatchJobExecutionNotFoundException 을 던진다")
    void givenMissingExecution_whenGet_thenThrowsNotFound() {
        given(jobRepository.getJobExecution(404L)).willReturn(null);

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(BatchJobExecutionNotFoundException.class);
    }

    @Test
    @DisplayName("JDBC DAO 가 없는 ID 에 EmptyResultDataAccessException 을 던져도 404 예외로 번역된다")
    void givenDaoEmptyResult_whenGet_thenThrowsNotFound() {
        given(jobRepository.getJobExecution(405L)).willThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.get(405L))
                .isInstanceOf(BatchJobExecutionNotFoundException.class);
    }
}
