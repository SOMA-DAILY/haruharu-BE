package org.son.haruharu.domain.mission.common.business.exception;

import org.son.haruharu.common.exception.ConflictException;

/** 미션 정의 변경이 이미 저장된 예약 안내의 조건을 깨뜨리는 경우. */
public class MissionAnnouncementConflictException extends ConflictException {
    public MissionAnnouncementConflictException() {
        super("MISSION_ANNOUNCEMENT_CONFLICT", "예약된 안내와 충돌하는 변경이에요. 안내를 먼저 수정하거나 취소해 주세요");
    }
}
