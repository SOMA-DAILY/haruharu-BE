package org.son.haruharu.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.admin.common.AdminRequestMeta;
import org.son.haruharu.admin.domain.audit.business.AdminAuditService;
import org.son.haruharu.admin.domain.audit.business.command.RecordAccessLogCommand;
import org.son.haruharu.admin.domain.audit.implement.AdminAuditActionResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * 어드민 API 요청마다 접속기록 1행을 남긴다(#554, 안전성 확보조치 기준 제8조).
 * <p>
 * afterCompletion 에서 기록하므로 컨트롤러가 예외로 끝나도(400/401/404/500) 남는다. 시큐리티 필터가
 * 디스패처 앞에서 끊는 401·403 은 여기 오지 않으며 그건 기존 CloudWatch warn 로그가 맡는다.
 * 기록 실패가 요청을 깨뜨리지 않도록 예외는 삼키고 error 로그만 남긴다.
 * 요청·응답 본문은 절대 읽지 않는다 — 이름·전화·채팅 내용이 섞인다.
 */
@Slf4j
@Component
public class AdminAccessLogInterceptor implements HandlerInterceptor {

    private final AdminAuditService adminAuditService;

    public AdminAccessLogInterceptor(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        try {
            String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (pattern == null) {
                pattern = request.getRequestURI();
            }
            Map<String, String> variables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            int status = response.getStatus();
            AdminAuditActionResolver.resolve(request.getMethod(), pattern, variables, status).ifPresent(resolved ->
                    adminAuditService.record(new RecordAccessLogCommand(authenticatedAdminId(),
                            (String) request.getAttribute(AdminRequestMeta.LOGIN_EMAIL_ATTRIBUTE),
                            AdminRequestMeta.clientIp(request), request.getMethod(), request.getRequestURI(),
                            resolved.action(), resolved.targetType(), resolved.targetId(), status,
                            AdminRequestMeta.userAgent(request))));
        } catch (RuntimeException e) {
            log.error("어드민 접속기록 저장 실패 {} {}", request.getMethod(), request.getRequestURI(), e);
        }
    }

    private static Long authenticatedAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long adminId) {
            return adminId;
        }
        return null;
    }
}
