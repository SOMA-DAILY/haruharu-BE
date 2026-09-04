package org.son.haruharu.batch.api.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidBatchJobParametersException extends BadRequestException {
    public InvalidBatchJobParametersException(String reason) {
        super("BATCH_JOB_PARAMETERS_INVALID", "배치 잡 파라미터가 유효하지 않습니다: " + reason);
    }
}
