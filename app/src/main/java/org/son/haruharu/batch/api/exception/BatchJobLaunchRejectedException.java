package org.son.haruharu.batch.api.exception;

import org.son.haruharu.common.exception.ConflictException;

/** JobRepository 가 재시작을 거부한 경우(#389) — 예: 재시작 불가로 정의된 잡, 재시작 상한 초과. */
public class BatchJobLaunchRejectedException extends ConflictException {
    public BatchJobLaunchRejectedException(String reason) {
        super("BATCH_JOB_LAUNCH_REJECTED", "배치 잡 시작이 거부되었습니다: " + reason);
    }
}
