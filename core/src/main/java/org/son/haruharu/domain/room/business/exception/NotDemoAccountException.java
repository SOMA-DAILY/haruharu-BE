package org.son.haruharu.domain.room.business.exception;

import org.son.haruharu.common.exception.ForbiddenException;

/**
 * 데모 계정이 아닌 유저가 데모 전용 조작(리셋 등)을 시도할 때(#267).
 * HTTP 경로에서는 DemoModeInterceptor가 선행 차단하지만, 서비스 계층의 이중 방어 가드가
 * 파괴적 조작(구름 원장 삭제)을 막기 위해 던진다. 예외 계층상 403(Forbidden).
 */
public class NotDemoAccountException extends ForbiddenException {

    public NotDemoAccountException() {
        super("ROOM_403_NOT_DEMO_ACCOUNT", "데모 계정만 사용할 수 있는 기능입니다.");
    }
}
