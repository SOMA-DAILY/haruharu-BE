package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.exception.UserNotFoundException;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
        if (token != null && jwtProvider.isValid(token) && jwtProvider.isAccessToken(token)) {
            try {
                Long userId = jwtProvider.extractUserId(token);
                if (!enforceStatus(request, response, userId)) {
                    return;
                }
                var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (NumberFormatException e) {
                // subject가 Long이 아님(예: 마이그레이션 이전 발급된 구 UUID subject 토큰).
                // 인증을 설정하지 않고 통과시켜 401로 자연스럽게 이어지도록 한다.
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
        // 로그인/갱신/로그아웃은 자체적으로 상태를 관리하므로(AuthService) 여기서 강제하지 않는다.
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        UserStatus status;
        try {
            status = userReader.getById(userId).status();
        } catch (UserNotFoundException e) {
            writeError(response, "USER_INACTIVE", "비활성화된 계정입니다.");
            return false;
        }
        if (!status.canAuthenticate()) {
            writeError(response, "USER_INACTIVE", "비활성화된 계정입니다.");
            return false;
        }
        if (status == UserStatus.ONBOARDING && !isOnboardingAllowed(path)) {
            writeError(response, "ONBOARDING_REQUIRED", "온보딩을 완료해야 이용할 수 있습니다.");
            return false;
        }
        return true;
    }

    private boolean isOnboardingAllowed(String path) {
        return ONBOARDING_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void writeError(HttpServletResponse response, String code, String message) throws IOException {
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
