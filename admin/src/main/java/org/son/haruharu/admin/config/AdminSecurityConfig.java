package org.son.haruharu.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.admin.security.AdminJwtAuthenticationFilter;
import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 어드민 서버 시큐리티. staff JWT(role claim) 기반 stateless 인증.
 * 로그인·헬스체크·API 문서만 공개하고 그 외는 인증 필요. 역할별 인가는 후속 API에서 requestMatcher로 추가한다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

    private final AdminJwtAuthenticationFilter adminJwtAuthenticationFilter;
    private final JsonMapper jsonMapper;

    public AdminSecurityConfig(AdminJwtAuthenticationFilter adminJwtAuthenticationFilter, JsonMapper jsonMapper) {
        this.adminJwtAuthenticationFilter = adminJwtAuthenticationFilter;
        this.jsonMapper = jsonMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 어드민 CORS. 극민감 데이터라 `*` 금지 — 설정(admin.cors.allowed-origins)의 정확한 오리진만 허용.
     * Bearer 토큰(Authorization 헤더) 사용, 쿠키 미사용이라 allowCredentials는 열지 않는다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${admin.cors.allowed-origins:}") String allowedOriginsCsv) {
        List<String> allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        // 방어: 어드민은 극민감 데이터라 전체 허용(*)을 절대 두지 않는다. 설정 실수 시 기동 자체를 막는다.
        if (allowedOrigins.contains("*")) {
            throw new IllegalStateException("admin.cors.allowed-origins 에 '*'는 허용되지 않습니다. 정확한 오리진만 지정하세요.");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                // 어드민 프런트(브라우저 SPA)는 다른 오리진 → CORS 필요. Security 체인에서 열어야
                // 프리플라이트(OPTIONS)가 인증 전에 처리된다. 허용 오리진은 corsConfigurationSource 빈의 화이트리스트.
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 티켓 상품 카탈로그 관리(생성·목록·수정)는 SUPER 전용.
                        .requestMatchers("/api/v1/admin/iap/products", "/api/v1/admin/iap/products/**").hasRole("SUPER")
                        // 운영진 티켓 지급은 재화를 만들어내는 쓰기라 SUPER 전용(#492).
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/users/*/tickets").hasRole("SUPER")
                        // 어드민 계정 관리(생성·목록·역할/상태 변경)는 SUPER 전용.
                        .requestMatchers("/api/v1/admin/admins", "/api/v1/admin/admins/**").hasRole("SUPER")
                        // 방 운영 쓰기(생성·코드 발급·성별 부여)는 SUPER·OPERATOR 전용(#318).
                        // 방 목록·상세 조회는 CS 포함 전체 허용 — 아래 anyRequest().authenticated() 로 떨어진다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/rooms",
                                "/api/v1/admin/rooms/*/invites", "/api/v1/admin/rooms/*/cancel")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/rooms/*/members/*")
                        .hasAnyRole("SUPER", "OPERATOR")
                        // 방 편성 쓰기(#457)는 SUPER·OPERATOR. 목록 조회(GET)는 로그인만(CS 는 화면이 버튼을 숨김).
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/rooms/*/missions/**")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers("/api/v1/admin/mission-images", "/api/v1/admin/mission-images/**")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/rooms/*/missions/**")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/rooms/*/missions/**")
                        .hasAnyRole("SUPER", "OPERATOR")
                        // 모집 트랙·기수 쓰기(생성·수정·공개·취소, #510)는 SUPER·OPERATOR. 목록 조회(GET)는 로그인만.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/recruit-tracks", "/api/v1/admin/cohorts",
                                "/api/v1/admin/cohorts/*/publish", "/api/v1/admin/cohorts/*/cancel")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/recruit-tracks/*", "/api/v1/admin/cohorts/*")
                        .hasAnyRole("SUPER", "OPERATOR")
                        // 기수 신청자 표(#515)는 번호가 들어 있어 조회도 SUPER·OPERATOR. 메모·승인도 같다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/cohorts/*/applications")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/cohort-applications/*/memo")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/cohort-applications/*/approve")
                        .hasAnyRole("SUPER", "OPERATOR")
                        // 초대코드는 "인증 없이 방에 들어갈 수 있는 자격증명"이라 조회도 쓰기와 같은 권한으로 막는다.
                        // 방 조회 권한(전체)에 코드 원문까지 딸려가면 CS 계정이 임의 방에 입장할 수 있다(#318 보안리뷰).
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/rooms/*/invites")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/rooms/*/selections", "/api/v1/admin/rooms/*/activities",
                                "/api/v1/admin/rooms/*/activity/summary")
                        .hasAnyRole("SUPER", "OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/app-update-policy").hasRole("SUPER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // 401·403 모두 ApiResponse 표준 포맷으로 응답(컨테이너 기본 에러 우회 방지).
                        // 실패 사유는 로그에만 남긴다 — 응답 노출은 계정 열거에 악용된다(#259).
                        // 어드민은 유저 개인정보를 다루므로 인증 실패 흔적이 더 중요하다(OWASP A09).
                        .authenticationEntryPoint((request, response, e) -> {
                            Object reason = request.getAttribute(AdminJwtAuthenticationFilter.AUTH_FAILURE_REASON);
                            log.warn("401 {} {} code=UNAUTHORIZED reason={}", request.getMethod(),
                                    request.getRequestURI(), reason == null ? "UNKNOWN" : reason);
                            writeError(response, 401, "UNAUTHORIZED", "인증이 필요합니다.");
                        })
                        .accessDeniedHandler((request, response, e) -> {
                            // 인증은 됐으나 역할이 모자란 경우 — 권한 상승 시도 탐지에 필요하다.
                            log.warn("403 {} {} code=FORBIDDEN adminId={}", request.getMethod(),
                                    request.getRequestURI(),
                                    request.getUserPrincipal() == null ? "-" : request.getUserPrincipal().getName());
                            writeError(response, 403, "FORBIDDEN", "권한이 없습니다.");
                        }))
                .addFilterBefore(adminJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<?> body = ApiResponse.error(new ApiErrorDetail(code, message));
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
