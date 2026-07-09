package org.son.haruharu.admin.config;

import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * 어드민 서버 시큐리티 골격.
 * <p>
 * 현재는 헬스체크·API 문서만 공개하고 그 외 요청은 전부 잠근다(default-deny).
 * formLogin·httpBasic을 명시적으로 제거해 Spring Boot 자동 in-memory 계정으로 인증이
 * 우회되지 않도록 한다 — 인증 수단 자체가 없으므로 보호 경로는 항상 401.
 * staff 계정(admin_accounts) 기반 JWT·RBAC 인증은 후속 이슈에서 이 설정을 확장한다.
 */
@Configuration
@EnableWebSecurity
public class AdminSecurityConfig {

    private final JsonMapper jsonMapper;

    public AdminSecurityConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Bean
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    ApiResponse<?> body = ApiResponse.error(new ApiErrorDetail("UNAUTHORIZED", "인증이 필요합니다."));
                    response.getWriter().write(jsonMapper.writeValueAsString(body));
                }))
                .build();
    }
}
