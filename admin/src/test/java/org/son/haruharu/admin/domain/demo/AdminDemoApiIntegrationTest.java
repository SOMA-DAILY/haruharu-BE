package org.son.haruharu.admin.domain.demo;

import java.util.List;

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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 데모 접근 코드 관리 API end-to-end(#352). RBAC(SUPER 전용) 배선·설정/조회를 실 HTTP로 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminDemoApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("SUPER는 데모 코드를 설정하고 조회할 수 있다")
    void givenSuper_whenSetAndGet_thenSucceeds() throws Exception {
        mockMvc.perform(put("/api/v1/admin/demo/access-code")
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"HARU-DEMO-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("HARU-DEMO-2026"))
                .andExpect(jsonPath("$.data.configured").value(true));

        mockMvc.perform(get("/api/v1/admin/demo/access-code").with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("HARU-DEMO-2026"));
    }

    @Test
    @DisplayName("OPERATOR는 데모 코드 관리 권한이 없어 403")
    void givenOperator_whenSet_thenForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/admin/demo/access-code")
                        .with(authentication(auth("OPERATOR"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("인증 없이 접근 시 401")
    void givenNoAuth_whenGet_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/demo/access-code"))
                .andExpect(status().isUnauthorized());
    }
}
