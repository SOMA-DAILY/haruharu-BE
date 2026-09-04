package org.son.haruharu.batch.api.dto;

import org.springframework.batch.core.step.StepExecution;

/** 스텝 실행 요약(#389). BATCH_STEP_EXECUTION 의 카운터를 그대로 노출한다 — 관측용. */
public record StepExecutionResponse(
        String name,
        String status,
        String exitCode,
        long readCount,
        long writeCount,
        long commitCount,
        long rollbackCount
) {
    public static StepExecutionResponse from(StepExecution step) {
        return new StepExecutionResponse(
                step.getStepName(),
                step.getStatus().name(),
                step.getExitStatus().getExitCode(),
                step.getReadCount(),
                step.getWriteCount(),
                step.getCommitCount(),
                step.getRollbackCount());
    }
}
