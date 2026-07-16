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
 * 원래 이 책임은 ALB access log 몫이라고 보고 앱에서 찍지 않았으나, 실제 haruharu-alb 는
 * access_logs.s3.enabled=false 라 <b>성공 요청(200)이 어디에도 기록되지 않았다.</b>
 * ALB 로그로 대체하기도 어렵다 — dev·prod 가 ALB 하나를 공용하므로 dev 만 켤 수 없고,
 * S3 배치 전송이라 약 5분 지연돼 실시간 QA·장애 대응에 쓸 수 없다.
 *
 * 실패 원인(도메인 에러코드)은 예외 객체를 볼 수 있는 GlobalExceptionHandler 가 남긴다 —
 * 이 필터는 응답 status 만 알 뿐 왜 그 status 인지 모르므로 둘은 이중 로깅이 아니라 상보 관계다.
 *
 * MdcLoggingFilter 바로 뒤에 둬서 traceId·userId 가 붙은 상태로 남는다.
 * 쿼리스트링은 남기지 않는다 — PII 가 섞일 수 있다(GlobalExceptionHandler 와 동일 기준).
 *
 * status 는 응답에 실제로 세팅된 값이라 <b>클라이언트 연결 종료도 500 으로 남는다</b> —
 * GlobalExceptionHandler 는 이를 DEBUG 로 낮추지만(#259 2-2) 그 판정은 예외 객체를 봐야 알 수 있고
 * 이 필터는 status 만 안다. access log 의 status 기준으로 5xx 알람을 걸면 탭 닫기가 섞여 들어온다 —
 * 5xx 알람은 ERROR 로그 기준으로 걸 것.
 *
 * 동기 요청 전제 — ASYNC 디스패치에서는 실행되지 않아(shouldNotFilterAsyncDispatch 기본 true)
 * SSE·DeferredResult 도입 시 duration 이 async 시작 시점까지만 측정되고 status 도 확정 전 값이 된다.
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

    /** 헬스체크는 30초마다 쌓이기만 하고 관측 가치가 없다. MdcLoggingFilter 와 동일 기준. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
