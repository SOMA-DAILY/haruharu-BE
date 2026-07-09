package org.son.haruharu.domain.auth.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.son.haruharu.domain.auth.business.AuthService;
import org.son.haruharu.domain.auth.business.command.SocialAuthCommand;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.son.haruharu.domain.auth.implement.ProviderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * provider_type를 String 대신 ProviderType enum으로 받도록 전환(#151)한 계약을 HTTP 경계에서 검증한다.
 * AuthService를 목으로 두어 OAuth 검증 흐름과 분리하고, 실제 ObjectMapper의 역직렬화 동작만 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialAuthRequestDeserializationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @Test
    @DisplayName("provider_type를 소문자로 보내도 대소문자 무시로 ProviderType enum에 매핑된다")
    void givenLowercaseProviderType_whenLogin_thenBindsEnumCaseInsensitive() throws Exception {
        given(authService.authenticate(any()))
                .willReturn(AuthTokenResponse.withStatus("access", "refresh", "ACTIVE"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider_type\": \"kakao\", \"id_token\": \"token\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SocialAuthCommand> captor = ArgumentCaptor.forClass(SocialAuthCommand.class);
        verify(authService).authenticate(captor.capture());
        assertThat(captor.getValue().providerType()).isEqualTo(ProviderType.KAKAO);
    }

    @Test
    @DisplayName("지원하지 않는 provider_type으로 로그인 요청 시 400을 반환한다")
    void givenUnsupportedProviderType_whenLogin_thenBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"provider_type\": \"naver\", \"id_token\": \"token\"}"))
                .andExpect(status().isBadRequest());
    }
}
