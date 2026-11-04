package org.son.haruharu.admin.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 접속기록(#554)에 남길 요청 메타. 컨트롤러와 인터셉터가 같이 쓰므로 common 에 둔다.
 * 접속지는 X-Forwarded-For 의 마지막 값 — ALB 가 실제 클라이언트 IP 를 맨 뒤에 붙이고, 앞쪽 값은 클라이언트가 위조할 수 있다.
 */
public final class AdminRequestMeta {

    /** 로그인 컨트롤러가 입력 이메일을 넘기는 요청 속성. 토큰 발급 전이라 SecurityContext 에 주체가 없다. */
    public static final String LOGIN_EMAIL_ATTRIBUTE = "haruharu.admin.audit.loginEmail";

    private AdminRequestMeta() {
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}
