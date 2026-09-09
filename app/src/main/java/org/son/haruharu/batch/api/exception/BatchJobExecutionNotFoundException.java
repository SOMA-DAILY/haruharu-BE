package org.son.haruharu.batch.api.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class BatchJobExecutionNotFoundException extends NotFoundException {
    public BatchJobExecutionNotFoundException(long executionId) {
        super("BATCH_JOB_EXECUTION_NOT_FOUND", "존재하지 않는 배치 실행입니다: execution_id=" + executionId);
    }
}
