package org.son.haruharu.devsupport.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidDevMembersException extends BadRequestException {
    public InvalidDevMembersException() {
        super("DEV_INVALID_MEMBERS", "멤버는 중복 없이 2~6명이어야 합니다.");
    }
}
