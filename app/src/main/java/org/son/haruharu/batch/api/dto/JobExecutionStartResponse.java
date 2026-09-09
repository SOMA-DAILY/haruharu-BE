package org.son.haruharu.batch.api.dto;

import org.springframework.batch.core.job.JobExecution;

/** 잡 시작 응답(#389). 오케스트레이터는 {@code executionId} 로 상태 조회를 폴링한다. */
public record JobExecutionStartResponse(
        long executionId,
        String jobName,
        String bucket,
        String status,
        JobExecutionStartOutcome outcome
) {
    public static JobExecutionStartResponse of(JobExecution execution, String bucket, JobExecutionStartOutcome outcome) {
        return new JobExecutionStartResponse(
                execution.getId(),
                execution.getJobInstance().getJobName(),
                bucket,
                execution.getStatus().name(),
                outcome);
    }
}
