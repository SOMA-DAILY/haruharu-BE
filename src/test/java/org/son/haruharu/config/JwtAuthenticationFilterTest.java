package org.son.haruharu.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtProvider jwtProvider;
    @Mock
    UserReader userReader;
    @Mock
    JsonMapper jsonMapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("서명은 유효하나 subject가 숫자가 아닌 토큰(구 UUID subject)이면 인증을 설정하지 않고 예외 없이 통과한다")
    void givenNonNumericSubjectToken_whenFilter_thenNoAuthenticationAndChainContinues() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider, userReader, jsonMapper);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        String legacyToken = "signed-but-uuid-subject";
        given(request.getHeader("Authorization")).willReturn("Bearer " + legacyToken);
        given(jwtProvider.isValid(legacyToken)).willReturn(true);
        given(jwtProvider.isAccessToken(legacyToken)).willReturn(true);
        given(jwtProvider.extractUserId(legacyToken))
            .willThrow(new NumberFormatException("For input string: \"3fa85f64-5717-4562-b3fc-2c963f66afa6\""));

        assertThatCode(() -> filter.doFilterInternal(request, response, chain)).doesNotThrowAnyException();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(request, response);
    }
}
