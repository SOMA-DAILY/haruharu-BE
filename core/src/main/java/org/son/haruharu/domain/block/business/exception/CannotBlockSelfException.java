package org.son.haruharu.domain.block.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class CannotBlockSelfException extends BadRequestException {

    public CannotBlockSelfException() {
        super("CANNOT_BLOCK_SELF", "자기 자신은 차단할 수 없습니다.");
    }
}
