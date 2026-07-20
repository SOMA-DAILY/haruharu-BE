package org.son.haruharu.demo.exception;

import org.son.haruharu.common.exception.ForbiddenException;

/** 데모 계정(demo_mode=true)이 아닌 유저가 /api/v1/demo/** 를 호출한 경우. */
public class DemoForbiddenException extends ForbiddenException {
    public DemoForbiddenException() {
        super("DEMO_FORBIDDEN", "데모 계정만 사용할 수 있는 기능입니다.");
    }
}
