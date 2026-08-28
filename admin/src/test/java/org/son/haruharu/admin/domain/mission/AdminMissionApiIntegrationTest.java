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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminMissionApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;

    private static final String CREATE_BODY = body("오늘의 풍경을 보여주세요", "DAILY_VIEW", "0.8");

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("SUPER는 미션을 생성하고 새 revision을 발행한 뒤 이력과 상태를 조회한다")
    void givenSuper_whenManageMission_thenPreservesImmutableRevisionHistory() throws Exception {
        long missionId = createMission(CREATE_BODY);

        mockMvc.perform(post("/api/v1/admin/missions/{id}/revisions", missionId)
                        .with(authentication(auth("SUPER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("새로운 풍경을 보여주세요", "DAILY_VIEW", "0.9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active_revision.revision_number").value(2))
                .andExpect(jsonPath("$.data.active_revision.description").value("새로운 풍경을 보여주세요"));

        mockMvc.perform(get("/api/v1/admin/missions/{id}/revisions", missionId)
                        .with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].revision_number").value(2))
                .andExpect(jsonPath("$.data.content[1].revision_number").value(1))
                .andExpect(jsonPath("$.data.content[1].description").value("오늘의 풍경을 보여주세요"));

        mockMvc.perform(patch("/api/v1/admin/missions/{id}/status", missionId)
                        .with(authentication(auth("SUPER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.active_revision.revision_number").value(2));
    }

    @Test
    @DisplayName("잘못된 메타데이터와 중복 eligibility는 400이며 부분 저장되지 않는다")
    void givenInvalidRevision_whenCreate_thenReturns400WithoutPartialMission() throws Exception {
        String invalid = CREATE_BODY.replace("\"DAILY_VIEW\"", "\"daily-view\"")
                .replace("{\"day_number\":1,\"period\":\"AFTERNOON\"}",
                        "{\"day_number\":1,\"period\":\"MORNING\"}");

        mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("SUPER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/admin/missions").with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("SAFE_FALLBACK 안전 불변식을 위반하면 400이며 부분 저장되지 않는다")
    void givenUnsafeFallback_whenCreate_thenReturns400WithoutPartialMission() throws Exception {
        String invalid = CREATE_BODY
                .replace("\"disclosure_depth\":\"LOW\"", "\"disclosure_depth\":\"HIGH\"")
                .replace("\"is_fallback\":false", "\"is_fallback\":true");

        mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("SUPER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSION_REVISION_INVALID"));

        mockMvc.perform(get("/api/v1/admin/missions").with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("null eligibility 요소는 400이며 부분 저장되지 않는다")
    void givenNullEligibilityElement_whenCreate_thenReturns400WithoutPartialMission() throws Exception {
        String invalid = CREATE_BODY.replace(
                "{\"day_number\":1,\"period\":\"AFTERNOON\"}", "null");

        mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("SUPER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/v1/admin/missions").with(authentication(auth("SUPER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("OPERATOR는 미션 관리 권한이 없어 403")
    void givenOperator_whenCreate_thenReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("인증 없이 접근 시 401")
    void givenNoAuthentication_whenList_thenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/missions"))
                .andExpect(status().isUnauthorized());
    }

    private long createMission(String body) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/missions")
                        .with(authentication(auth("SUPER")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.active_revision.revision_number").value(1))
                .andExpect(jsonPath("$.data.active_revision.is_fallback").value(false))
                .andExpect(jsonPath("$.data.active_revision.eligibilities[0].period").value("MORNING"))
                .andExpect(jsonPath("$.data.active_revision.eligibilities[1].period").value("AFTERNOON"))
                .andExpect(jsonPath("$.data.active_revision.eligibilities.length()").value(2))
                .andReturn();
        return jsonMapper.readTree(created.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private static String body(String description, String cluster, String score) {
        return """
                {
                  "description":"%s",
                  "disclosure_depth":"LOW",
                  "mission_goal":"OBSERVATION",
                  "activation_channel":"VIDEO",
                  "interaction_scope":"SELF",
                  "participation_burden":"LOW",
                  "privacy_risk":"LOW",
                  "preference_leak_risk":"NONE",
                  "capture_difficulty":"LOW",
                  "semantic_cluster":"%s",
                  "base_quality_score":%s,
                  "is_fallback":false,
                  "eligibilities":[
                    {"day_number":1,"period":"MORNING"},
                    {"day_number":1,"period":"AFTERNOON"}
                  ]
                }
                """.formatted(description, cluster, score);
    }
}
