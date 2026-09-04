package org.son.haruharu.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.batch.config.InternalBatchProperties;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내부 배치 토큰 필터 단위 테스트(#389). 헤더 유무·일치·미설정(fail-closed)에 따른 통과/401 을 검증한다.
 */
class InternalBatchTokenFilterTest {

    private static final String TOKEN = "unit-test-internal-token-at-least-32-chars";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private InternalBatchTokenFilter filter(String configuredToken) {
        return new InternalBatchTokenFilter(new InternalBatchProperties(configuredToken), jsonMapper);
    }

    private MockHttpServletRequest request(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/batch/jobs/x/executions");
        if (headerValue != null) {
            request.addHeader(InternalBatchTokenFilter.HEADER, headerValue);
        }
        return request;
    }

    @Test
    @DisplayName("올바른 토큰이면 ROLE_INTERNAL_BATCH 인증을 설정하고 체인을 계속한다")
    void givenValidToken_whenFilter_thenAuthenticatedAndChainContinues() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(TOKEN).doFilter(request(TOKEN), response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_INTERNAL_BATCH");
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("헤더가 없으면 401 JSON 을 쓰고 체인을 중단한다")
    void givenNoHeader_whenFilter_then401AndChainStops() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(TOKEN).doFilter(request(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"isSuccess\":false").contains("UNAUTHORIZED");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("토큰이 틀리면 401 을 쓰고 체인을 중단한다")
    void givenWrongToken_whenFilter_then401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(TOKEN).doFilter(request("wrong-token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("서버에 토큰이 설정돼 있지 않으면(빈 값) 어떤 헤더로도 통과하지 못한다 — fail-closed")
    void givenBlankConfiguredToken_whenFilter_thenAlways401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter("").doFilter(request(""), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
