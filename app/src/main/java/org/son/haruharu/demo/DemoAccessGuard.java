package org.son.haruharu.demo;

import org.son.haruharu.demo.exception.DemoForbiddenException;
import org.son.haruharu.domain.user.implement.UserReader;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 데모 API 접근 게이트(#264). 데모 조작 API(#265·#266·#267)는 프로덕션에도 로드되므로,
 * 프로필이 아닌 호출 "계정"의 demo_mode 플래그로 인가한다.
 * <p>
 * userId를 직접 받지 않고 {@link Authentication}만 받는다 — 호출부가 path/body의 임의 userId를 넘겨
 * "남의 demo_mode"를 검사하는 오용(IDOR류 우회)을 시그니처 수준에서 차단하기 위함이다.
 * 실제 강제는 {@code DemoModeInterceptor}가 /api/v1/demo/** 전 경로에 적용한다.
 */
@Component
public class DemoAccessGuard {

    private final UserReader userReader;

    public DemoAccessGuard(UserReader userReader) {
        this.userReader = userReader;
    }

    /** 인증 주체(principal=userId)가 데모 계정이 아니면 403(DEMO_FORBIDDEN). 인증이 없으면 fail-closed로 거부. */
    public void requireDemoAccount(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new DemoForbiddenException();
        }
        if (!userReader.getById(userId).demoMode()) {
            throw new DemoForbiddenException();
        }
    }
}
