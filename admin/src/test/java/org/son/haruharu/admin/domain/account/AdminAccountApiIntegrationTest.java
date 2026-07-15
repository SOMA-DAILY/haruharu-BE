package org.son.haruharu.admin.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAccountApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;

    private static final String CREATE_BODY = """
            {"email":"ops@haruharu.co.kr","password":"Str0ngP@ss","name":"운영자","role":"OPERATOR"}""";

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("SUPER는 계정을 생성·목록·역할/상태 변경할 수 있고 비밀번호는 노출되지 않는다")
    void super_create_list_update() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/admins")
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ops@haruharu.co.kr"))
                .andExpect(jsonPath("$.data.role").value("OPERATOR"))
                .andExpect(jsonPath("$.data.password_hash").doesNotExist())
                .andReturn();
        long id = jsonMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/admins").with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email == 'ops@haruharu.co.kr')]").exists());

        mockMvc.perform(patch("/api/v1/admin/admins/{id}", id)
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.role").value("OPERATOR")); // 유지
    }

    @Test
    @DisplayName("중복 이메일 생성 시 409")
    void create_duplicate_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/admins")
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/admins")
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ACCOUNT_DUPLICATE"));
    }

    @Test
    @DisplayName("OPERATOR는 계정 관리 권한이 없어 403")
    void operator_create_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/admins")
                        .with(authentication(auth("OPERATOR"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("인증 없이 접근 시 401")
    void noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/admins"))
                .andExpect(status().isUnauthorized());
    }
}
