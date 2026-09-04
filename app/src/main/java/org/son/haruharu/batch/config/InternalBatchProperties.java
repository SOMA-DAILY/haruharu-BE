package org.son.haruharu.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 내부 배치 트리거 API(#389) 인증 설정. 외부 오케스트레이터(Airflow)가 {@code X-Internal-Token} 헤더로 보내는
 * 공유 시크릿. 값이 비어 있으면 필터는 모든 요청을 거부한다(fail-closed) — 설정 누락이 무인증 개방으로 이어지지 않게.
 * <p>
 * 설정돼 있다면 최소 32자를 강제한다(보안 리뷰). ALB 차단 전까지 이 토큰이 유일한 방어선이라 약한 값으로 기동되면 안 된다.
 */
@ConfigurationProperties(prefix = "haruharu.batch.internal")
public record InternalBatchProperties(@DefaultValue("") String token) {

    public static final int MIN_TOKEN_LENGTH = 32;

    public InternalBatchProperties {
        if (token != null && !token.isBlank() && token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalArgumentException(
                    "haruharu.batch.internal.token(INTERNAL_BATCH_TOKEN) 은 최소 " + MIN_TOKEN_LENGTH + "자여야 한다");
        }
    }
}
