package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 1건당 access log 1줄(method·path·status·duration)을 남긴다(#259).
 *
 * ALB access log 몫이라고 봤으나 haruharu-alb 는 access_logs.s3.enabled=false 라
 * 성공 요청(200)이 어디에도 기록되지 않았다. ALB 로그를 켜는 것도 대안이 못 된다 —
 * dev·prod 가 ALB 하나를 공용해 dev 만 켤 수 없고, S3 배치라 약 5분 지연된다.
 *
 * 쿼리스트링은 남기지 않는다 — PII 가 섞인다(GlobalExceptionHandler 와 동일 기준).
 *
 * status 는 응답에 세팅된 값이라 <b>클라 연결 종료도 500 으로 남는다</b> — GlobalExceptionHandler 는
 * 이를 DEBUG 로 낮추지만(2-2) 그 판정은 예외 객체를 봐야 안다.
 * 5xx 알람은 access log 가 아니라 ERROR 로그 기준으로 걸 것.
 *
 * 동기 요청 전제 — ASYNC 디스패치에선 실행되지 않아 SSE 도입 시 duration·status 가 부정확해진다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class AccessLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 예외로 빠져나가도 남긴다 — 실패한 요청이야말로 기록이 필요하다.
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
        }
    }

    /** 헬스체크는 30초마다 쌓이기만 하고 관측 가치가 없다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
