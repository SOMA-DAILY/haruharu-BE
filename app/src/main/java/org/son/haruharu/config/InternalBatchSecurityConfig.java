package org.son.haruharu.config;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.batch.config.InternalBatchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /internal/batch/**} 전용 시큐리티 체인(#389). 유저 JWT 체인(SecurityConfig)보다 먼저 매칭되어
 * 이 경로에는 공유 시크릿 필터만 적용한다 — 유저 토큰으로는 내부 API 에 들어올 수 없다.
 * <p>
 * 범위를 배치 경로로 한정한다. 나중에 다른 {@code /internal/*} 엔드포인트가 생겨도 이 배치 토큰이 자동으로 열어주지 않고,
 * 유저 체인의 {@code anyRequest().authenticated()} 로 떨어져 안전하게 막힌다.
 * <p>
 * 이중 방어: 필터가 통과시킨 요청만 {@code ROLE_INTERNAL_BATCH} 를 갖고, 인가 규칙이 그 권한을 요구한다.
 * 필터를 우회해도 인가에서 막힌다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(InternalBatchProperties.class)
public class InternalBatchSecurityConfig {

    private final InternalBatchProperties properties;
    private final JsonMapper jsonMapper;

    public InternalBatchSecurityConfig(InternalBatchProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalBatchFilterChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/internal/batch/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(InternalBatchTokenFilter.ROLE))
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, e) -> {
                log.warn("401 {} {} code=UNAUTHORIZED reason=NO_INTERNAL_AUTHORITY",
                        request.getMethod(), request.getRequestURI());
                InternalBatchTokenFilter.writeUnauthorized(response, jsonMapper);
            }))
            .addFilterBefore(new InternalBatchTokenFilter(properties, jsonMapper),
                    UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
