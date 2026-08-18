package org.son.haruharu.admin.domain.mission.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class AdminMissionDuplicateException extends ConflictException {
    public AdminMissionDuplicateException() {
        super("ADMIN_MISSION_DUPLICATE", "이미 존재하는 미션 문구입니다.");
    }
}
