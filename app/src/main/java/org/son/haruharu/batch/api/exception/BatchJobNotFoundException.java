package org.son.haruharu.batch.api.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class BatchJobNotFoundException extends NotFoundException {
    public BatchJobNotFoundException(String jobName) {
        super("BATCH_JOB_NOT_FOUND", "등록되지 않은 배치 잡입니다: job_name=" + jobName);
    }
}
