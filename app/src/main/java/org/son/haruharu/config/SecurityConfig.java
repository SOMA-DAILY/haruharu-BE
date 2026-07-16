package org.son.haruharu.config;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.ApiErrorDetail;
import org.son.haruharu.common.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonMapper jsonMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, JsonMapper jsonMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jsonMapper = jsonMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                // RevenueCat webhook은 JWT가 아닌 공유 시크릿(Authorization 헤더)으로 인증한다.
                .requestMatchers(HttpMethod.POST, "/api/v1/iap/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/value-questions").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/meta/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // WebSocket 핸드셰이크는 HTTP 레벨에서 열고, 실제 인증은 STOMP CONNECT(StompAuthChannelInterceptor)에서 한다.
                .requestMatchers("/ws-chat/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                // 실패 사유는 JwtAuthenticationFilter 가 요청 속성에 심어둔다 — AuthenticationException 은
                // 여기까지 사유를 싣고 오지 않는다(토큰 검증은 필터가 끝낸 뒤라 이 예외엔 정보가 없다).
                // 사유는 로그에만 남기고 응답은 뭉뚱그린다 — 노출하면 계정 열거·토큰 대조에 악용된다(#259).
                // 스키마는 GlobalExceptionHandler.warn()과 맞춘다: {status} {method} {uri} code=... reason=...
                Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_REASON);
                log.warn("401 {} {} code=UNAUTHORIZED reason={}", request.getMethod(), request.getRequestURI(),
                        reason == null ? "UNKNOWN" : reason);
                response.setStatus(401);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                ApiResponse<?> body = ApiResponse.error(new ApiErrorDetail("UNAUTHORIZED", "인증이 필요합니다."));
                response.getWriter().write(jsonMapper.writeValueAsString(body));
            }))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
