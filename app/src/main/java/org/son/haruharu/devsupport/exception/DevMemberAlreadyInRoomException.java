package org.son.haruharu.devsupport.exception;

import org.son.haruharu.common.exception.ConflictException;

public class DevMemberAlreadyInRoomException extends ConflictException {
    public DevMemberAlreadyInRoomException(Long userId) {
        super("DEV_MEMBER_IN_ROOM", "이미 진행 중인 방이 있는 유저입니다: userId=" + userId);
    }
}
