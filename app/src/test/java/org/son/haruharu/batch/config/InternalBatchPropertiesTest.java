package org.son.haruharu.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 내부 토큰 최소 엔트로피 강제(#389 보안 리뷰). 짧은 토큰은 기동 실패, 빈 값은 fail-closed 로 필터가 처리한다. */
class InternalBatchPropertiesTest {

    @Test
    @DisplayName("32자 미만 토큰은 기동 시 거부된다")
    void givenShortToken_whenConstruct_thenThrows() {
        assertThatThrownBy(() -> new InternalBatchProperties("short-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("빈 값(미설정)은 허용된다 — 필터가 fail-closed 로 전부 거부한다")
    void givenBlank_whenConstruct_thenAllowed() {
        assertThatCode(() -> new InternalBatchProperties("")).doesNotThrowAnyException();
        assertThatCode(() -> new InternalBatchProperties(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("32자 이상 토큰은 허용된다")
    void givenLongToken_whenConstruct_thenAllowed() {
        assertThatCode(() -> new InternalBatchProperties("a".repeat(32))).doesNotThrowAnyException();
    }
}
