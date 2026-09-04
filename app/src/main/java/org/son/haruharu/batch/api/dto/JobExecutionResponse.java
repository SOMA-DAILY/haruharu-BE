package org.son.haruharu.batch.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.batch.core.job.JobExecution;

/**
 * 잡 실행 상태 응답(#389). {@code exitMessage} 는 실패 시 스택트레이스 요약이 실린다 — 내부 API 라 그대로 노출한다.
 * (BATCH_JOB_EXECUTION.EXIT_MESSAGE 는 2500자 컷이며 유저 데이터가 아닌 예외 문자열이다.)
 */
public record JobExecutionResponse(
        long executionId,
        String jobName,
        String bucket,
        String status,
        String exitCode,
        String exitMessage,
        LocalDateTime createTime,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<StepExecutionResponse> steps
) {
    public static JobExecutionResponse from(JobExecution execution) {
        String exitMessage = execution.getExitStatus().getExitDescription();
        return new JobExecutionResponse(
                execution.getId(),
                execution.getJobInstance().getJobName(),
                execution.getJobParameters().getString("bucket"),
                execution.getStatus().name(),
                execution.getExitStatus().getExitCode(),
                exitMessage == null || exitMessage.isBlank() ? null : exitMessage,
                execution.getCreateTime(),
                execution.getStartTime(),
                execution.getEndTime(),
                execution.getStepExecutions().stream().map(StepExecutionResponse::from).toList());
    }
}
