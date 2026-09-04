package org.son.haruharu.batch.api.exception;

import org.son.haruharu.common.exception.ConflictException;

/**
 * 같은 식별 파라미터의 실행이 STARTED/STARTING 상태(#389). 정상 동시성이거나 프로세스 사망으로 남은 좀비다.
 * 오케스트레이터는 잠시 후 재시도하거나 {@code executionId} 로 상태를 확인한다.
 * 좀비 정리(abandon)는 범위 밖 — BatchInfraConfig 의 트랜잭션매니저 override 와 함께 별도 이슈.
 */
public class BatchJobAlreadyRunningException extends ConflictException {

    private final Long executionId;

    public BatchJobAlreadyRunningException(Long executionId) {
        super("BATCH_JOB_ALREADY_RUNNING",
                "이미 실행 중인 배치 잡입니다: execution_id=" + (executionId == null ? "unknown" : executionId));
        this.executionId = executionId;
    }

    public Long getExecutionId() {
        return executionId;
    }
}
