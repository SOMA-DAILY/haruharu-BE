package org.son.haruharu.demo.business.exception;

import org.son.haruharu.common.exception.UnauthorizedException;

/** 데모 접근 코드가 없거나 틀림(#352). 코드 미설정(로그인 닫힘) 포함 — 사유는 응답에 노출하지 않는다. */
public class DemoAccessDeniedException extends UnauthorizedException {

    public DemoAccessDeniedException() {
        super("DEMO_ACCESS_DENIED", "데모 접근 코드가 유효하지 않습니다.");
    }
}
