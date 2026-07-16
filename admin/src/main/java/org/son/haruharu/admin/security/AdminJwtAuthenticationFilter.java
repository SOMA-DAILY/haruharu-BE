package org.son.haruharu.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.utils.JwtValidation;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 어드민 access JWT를 검증해 SecurityContext에 인증을 설정한다.
 * 토큰이 없거나 유효하지 않으면 <b>인증 미설정으로 통과</b>시켜 뒤단(entryPoint)이 401을 반환하게 한다.
 * 파싱 예외가 500으로 새지 않도록 방어한다(유저 서버 회귀 교훈).
 */
@Component
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    /** 인증 실패 사유를 EntryPoint 로 전달하는 요청 속성 키(#259). 유저 서버와 동일 방침. */
    public static final String AUTH_FAILURE_REASON = "haruharu.admin.authFailureReason";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminJwtProvider adminJwtProvider;

    public AdminJwtAuthenticationFilter(AdminJwtProvider adminJwtProvider) {
        this.adminJwtProvider = adminJwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            request.setAttribute(AUTH_FAILURE_REASON, "NO_TOKEN");
        } else {
            JwtValidation validation = adminJwtProvider.validate(token);
            if (validation != JwtValidation.VALID) {
                request.setAttribute(AUTH_FAILURE_REASON, validation.name());
            } else if (!adminJwtProvider.isAccessToken(token)) {
                request.setAttribute(AUTH_FAILURE_REASON, "NOT_ACCESS_TOKEN");
            } else {
                try {
                    Long adminId = adminJwtProvider.extractAdminId(token);
                    AdminRole role = adminJwtProvider.extractRole(token);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            adminId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (RuntimeException e) {
                    // 손상된 subject/role claim → 인증 미설정으로 통과(entryPoint가 401 처리)
                    SecurityContextHolder.clearContext();
                    request.setAttribute(AUTH_FAILURE_REASON, "MALFORMED_CLAIM");
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
