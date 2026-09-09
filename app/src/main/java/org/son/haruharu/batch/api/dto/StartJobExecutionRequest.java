package org.son.haruharu.batch.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 잡 시작 요청(#389). {@code bucket} 은 Spring Batch 식별 파라미터가 되어 {@code (JOB_NAME, JOB_KEY)} 유니크의
 * 재료가 된다 — 오케스트레이터의 logical date(일 잡은 {@code yyyy-MM-dd}, 분 잡은 {@code yyyy-MM-ddTHH:mm})와 1:1.
 * 잡마다 포맷이 달라 여기서는 문자 집합과 길이만 제한한다.
 */
public record StartJobExecutionRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9T:_-]+", message = "bucket 은 영문·숫자·'T' ':' '_' '-' 만 허용한다")
        String bucket
) {
}
