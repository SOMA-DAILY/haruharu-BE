package org.son.haruharu.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.context.annotation.Profile;

/**
 * /api/v1/demo/** 전 경로에 데모 계정 인가를 강제하는 인터셉터(#264).
 * 컨트롤러가 개별적으로 가드를 호출하는 방식은 한 곳이라도 누락되면 게이트가 뚫리므로,
 * 경로 기반 인터셉터로 진입 자체를 구조적으로 막는다. 인증(JWT)은 SecurityFilter가 선행 처리한다.
 */
@Component
@Profile("!prod") // 데모는 dev/local/test에서만 로드, prod 제외(#284)
public class DemoModeInterceptor implements HandlerInterceptor {

    private final DemoAccessGuard demoAccessGuard;

    public DemoModeInterceptor(DemoAccessGuard demoAccessGuard) {
        this.demoAccessGuard = demoAccessGuard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        demoAccessGuard.requireDemoAccount(SecurityContextHolder.getContext().getAuthentication());
        return true;
    }
}
