package org.son.haruharu.admin.domain.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.admin.domain.account.storage.AdminAccountEntity;
import org.son.haruharu.admin.domain.account.storage.AdminAccountJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // 각 테스트 롤백 — BaseEntity 소프트삭제라 deleteAll이 unique email을 남기는 문제 회피
class AdminAuthApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AdminAccountJpaRepository adminAccountJpaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JsonMapper jsonMapper;

    private static final String EMAIL = "admin@haruharu.app";
    private static final String PASSWORD = "P@ssw0rd!";

    @BeforeEach
    void seed() {
        adminAccountJpaRepository.save(AdminAccountEntity.from(
                AdminAccount.create(EMAIL, passwordEncoder.encode(PASSWORD), "슈퍼관리자", AdminRole.SUPER)));
    }

    private String loginAndGetAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.access_token").isNotEmpty())
                .andExpect(jsonPath("$.data.refresh_token").isNotEmpty())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("access_token").asString();
    }

    @Test
    @DisplayName("올바른 자격증명으로 로그인 후 발급 토큰으로 /me 조회 시 계정·역할이 반환된다")
    void login_thenMe_withToken_returnsAccount() throws Exception {
        String accessToken = loginAndGetAccessToken();

        mockMvc.perform(get("/api/v1/admin/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("SUPER"));
    }

    @Test
    @DisplayName("토큰 없이 보호 엔드포인트(/me) 접근 시 401")
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401(계정 존재 여부 비노출)")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ADMIN_LOGIN_FAILED"));
    }
}
