package org.son.haruharu.admin.config;

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
                        // 코인 상품 카탈로그 관리(생성·목록·수정)는 SUPER 전용.
                        .requestMatchers("/api/v1/admin/iap/products", "/api/v1/admin/iap/products/**").hasRole("SUPER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // 401·403 모두 ApiResponse 표준 포맷으로 응답(컨테이너 기본 에러 우회 방지).
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, 401, "UNAUTHORIZED", "인증이 필요합니다."))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, 403, "FORBIDDEN", "권한이 없습니다.")))
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
