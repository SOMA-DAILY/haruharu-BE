package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class ProfileFieldNotYetUnlockableException extends ConflictException {

    public ProfileFieldNotYetUnlockableException() {
        super("ROOM_409_FIELD_LOCKED_DAY", "아직 해당 프로필 항목을 해금할 수 있는 날이 아닙니다.");
    }
}
