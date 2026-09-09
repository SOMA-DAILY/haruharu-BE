package org.son.haruharu.domain.block.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class BlockAlreadyExistsException extends ConflictException {

    public BlockAlreadyExistsException() {
        super("BLOCK_ALREADY_EXISTS", "이미 차단한 사용자입니다.");
    }
}
