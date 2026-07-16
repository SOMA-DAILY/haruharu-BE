package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.exception.UserNotFoundException;
import org.son.haruharu.utils.JwtProvider;
import org.son.haruharu.utils.JwtValidation;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 인증 실패 사유를 EntryPoint 로 전달하는 요청 속성 키(#259).
     * 401 응답은 SecurityConfig 의 EntryPoint 가 직접 쓰는데, 그 지점엔 실패 사유가 없어 로그를 남길 수 없었다.
     */
    static final String AUTH_FAILURE_REASON = "haruharu.authFailureReason";

    // ONBOARDING(온보딩 미완) 유저가 접근할 수 있는 경로 prefix. 그 외 보호 자원(매칭·채팅·방·홈·구름·영상)은 차단한다.
    private static final List<String> ONBOARDING_ALLOWED_PREFIXES = List.of(
        "/api/v1/users/me",
        "/api/v1/value-answers",
        "/api/v1/value-questions"
    );

    private final JwtProvider jwtProvider;
    private final UserReader userReader;
    private final JsonMapper jsonMapper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, UserReader userReader, JsonMapper jsonMapper) {
        this.jwtProvider = jwtProvider;
        this.userReader = userReader;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            request.setAttribute(AUTH_FAILURE_REASON, "NO_TOKEN");
        } else {
            JwtValidation validation = jwtProvider.validate(token);
            if (validation != JwtValidation.VALID) {
                // 사유를 EntryPoint 로 넘긴다 — 401 을 실제로 낼지(=보호 자원인지)는 인가 단계가 결정하므로
                // 여기서 바로 로깅하면 permitAll 경로의 무해한 실패까지 남는다(#259).
                request.setAttribute(AUTH_FAILURE_REASON, validation.name());
            } else if (!jwtProvider.isAccessToken(token)) {
                request.setAttribute(AUTH_FAILURE_REASON, "NOT_ACCESS_TOKEN");
            } else {
                try {
                    Long userId = jwtProvider.extractUserId(token);
                    if (!enforceStatus(request, response, userId)) {
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // MdcLoggingFilter는 인증 전에 실행되므로 userId를 알 수 없다. 인증을 마친 여기서 채운다(#259).
                    MDC.put(MdcLoggingFilter.USER_ID, userId.toString());
                } catch (NumberFormatException e) {
                    // subject가 Long이 아님(예: 마이그레이션 이전 발급된 구 UUID subject 토큰).
                    // 인증을 설정하지 않고 통과시켜 401로 자연스럽게 이어지도록 한다.
                    request.setAttribute(AUTH_FAILURE_REASON, "NON_NUMERIC_SUBJECT");
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 요청 시점의 최신 UserStatus를 검증해 차단 계정과 온보딩 미완 계정의 접근을 막는다.
     * 토큰 기반 세션은 상태 전환(정지·탈퇴) 후에도 만료 전까지 유효하므로 요청마다 재확인한다.
     * (요청당 1회 조회 비용 발생 — 병목 시 캐시 도입 여지)
     *
     * @return true면 통과, false면 이미 에러 응답을 기록했으므로 필터 체인을 중단해야 한다.
     */
    private boolean enforceStatus(HttpServletRequest request, HttpServletResponse response, Long userId) throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        // 로그인/갱신/로그아웃은 자체적으로 상태를 관리하므로(AuthService) 여기서 강제하지 않는다.
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        UserStatus status;
        try {
            status = userReader.getById(userId).status();
        } catch (UserNotFoundException e) {
            writeError(response, method, path, "USER_INACTIVE", "비활성화된 계정입니다.", userId);
            return false;
        }
        if (!status.canAuthenticate()) {
            writeError(response, method, path, "USER_INACTIVE", "비활성화된 계정입니다.", userId);
            return false;
        }
        if (status == UserStatus.ONBOARDING && !isOnboardingAllowed(path)) {
            writeError(response, method, path, "ONBOARDING_REQUIRED", "온보딩을 완료해야 이용할 수 있습니다.", userId);
            return false;
        }
        return true;
    }

    private boolean isOnboardingAllowed(String path) {
        return ONBOARDING_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * 403 을 직접 응답한다 — GlobalExceptionHandler 에 도달하지 않으므로 로그도 여기서 남긴다(#259).
     * 제재 우회 시도(BANNED 유저의 반복 접근)를 탐지하려면 흔적이 필요하다(OWASP A09).
     */
    private void writeError(HttpServletResponse response, String method, String path, String code, String message,
                            Long userId) throws IOException {
        // 스키마는 GlobalExceptionHandler.warn()과 맞춘다: {status} {method} {uri} code=... — 알람·파싱 기준이 된다.
        log.warn("403 {} {} code={} userId={}", method, path, code, userId);
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<?> body = ApiResponse.error(new ApiErrorDetail(code, message));
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
