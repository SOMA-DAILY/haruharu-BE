package org.son.haruharu.domain.mission.chat.business.exception;
import org.son.haruharu.common.exception.ConflictException;
public class MissionDispatchRejectedException extends ConflictException {
    public MissionDispatchRejectedException(String reason) {
        super(reason, "현재 방 또는 미션 상태에서는 안내를 실행할 수 없습니다.");
    }
}
