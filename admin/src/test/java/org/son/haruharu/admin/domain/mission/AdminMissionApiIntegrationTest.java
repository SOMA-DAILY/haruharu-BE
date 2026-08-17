package org.son.haruharu.admin.domain.mission;

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

/**
 * 미션 카탈로그 관리 API의 라우팅·AdminSecurityConfig RBAC 배선·@Valid 직렬화를 end-to-end로 검증.
 * (product의 AdminIapProductApiIntegrationTest와 동일 구조 — 새 SUPER 매처가 실제 적용되는지 확인.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminMissionApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;

    private static final String CREATE_BODY = """
            {"description":"오늘 먹은 가장 맛있는 음식을 보여주세요","points":5}""";

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("SUPER는 미션을 생성·목록·수정할 수 있다")
    void super_create_list_update() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("오늘 먹은 가장 맛있는 음식을 보여주세요"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();
        long id = jsonMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();

        mockMvc.perform(get("/api/v1/admin/missions").with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.description == '오늘 먹은 가장 맛있는 음식을 보여주세요')]").exists());

        mockMvc.perform(patch("/api/v1/admin/missions/{id}", id)
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.description").value("오늘 먹은 가장 맛있는 음식을 보여주세요")); // 유지
    }

    @Test
    @DisplayName("중복 문구 생성 시 409")
    void create_duplicate_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("SUPER"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ADMIN_MISSION_DUPLICATE"));
    }

    @Test
    @DisplayName("OPERATOR는 미션 관리 권한이 없어 403")
    void operator_create_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("OPERATOR"))).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("인증 없이 접근 시 401")
    void noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/missions"))
                .andExpect(status().isUnauthorized());
    }
}
