package org.son.haruharu.domain.block.storage.exception;

import org.son.haruharu.common.exception.NotFoundException;

public class BlockNotFoundException extends NotFoundException {

    public BlockNotFoundException() {
        super("BLOCK_NOT_FOUND", "차단하지 않은 사용자입니다.");
    }
}
