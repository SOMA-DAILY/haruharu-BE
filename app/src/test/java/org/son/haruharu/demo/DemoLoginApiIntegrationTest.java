package org.son.haruharu.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.demoaccess.implement.DemoAccessCode;
import org.son.haruharu.domain.demoaccess.storage.DemoAccessCodeEntity;
import org.son.haruharu.domain.demoaccess.storage.DemoAccessCodeJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데모 로그인 API end-to-end(#352 PR1). 어드민 코드 게이트 + JWT 발급 + 계정 lazy 생성을 실 HTTP로 검증한다.
 * 데모 계정은 설계상 영구 싱글턴(리셋은 방만 삭제)이라 테스트 간 클린 없이 멱등 재사용을 그대로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoLoginApiIntegrationTest {

    private static final String CODE = "HARU-DEMO-2026";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired JwtProvider jwtProvider;
    @Autowired UserCredentialReader userCredentialReader;
    @Autowired DemoAccessCodeJpaRepository demoAccessCodeJpaRepository;

    @BeforeEach
    void setCode() {
        demoAccessCodeJpaRepository.save(DemoAccessCodeEntity.from(DemoAccessCode.of(CODE)));
    }

    private Long userIdOf(MvcResult result) throws Exception {
        String accessToken = jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("access_token").asString();
        return jwtProvider.extractUserId(accessToken);
    }

    @Test
    @DisplayName("올바른 코드로 데모 로그인하면 200·유효 토큰을 받고 DEMO 크레덴셜이 토큰 userId와 매핑된다")
    void givenValidCode_whenDemoLogin_thenTokensAndCredentialMapped() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + CODE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.data.user_status").value("ACTIVE"))
                .andReturn();

        String accessToken = jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("access_token").asString();
        assertThat(jwtProvider.isAccessToken(accessToken)).isTrue();

        assertThat(userCredentialReader.findByProviderTypeAndProviderId("DEMO", "haruharu-demo"))
                .isPresent()
                .get()
                .extracting("userId")
                .isEqualTo(userIdOf(result));
    }

    @Test
    @DisplayName("틀린 코드로 데모 로그인하면 401 DEMO_ACCESS_DENIED")
    void givenWrongCode_whenDemoLogin_thenUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/demo/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"WRONG-CODE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("DEMO_ACCESS_DENIED"));
    }
}
