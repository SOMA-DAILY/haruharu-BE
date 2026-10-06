package org.son.haruharu.domain.room;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutomaticMissionRetirementIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("구형 미션 조회·노출 API는 인증된 요청에도 404이며 API 문서에서 제외된다")
    void givenAuthenticatedUser_whenRequestRetiredMissions_then404() throws Exception {
        var auth = authentication(new UsernamePasswordAuthenticationToken(482L, null, List.of()));
        mvc.perform(get("/api/v1/rooms/482/missions").with(auth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/rooms/482/missions/1/impression").with(auth))
                .andExpect(status().isNotFound());
        String specification = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(specification).doesNotContain("/missions", "mission-recording", "mission-image", "/caption");
        assertThat(specification).contains("/api/v1/rooms/{roomId}/video-posts", "/api/v1/rooms/{roomId}/today");
    }
}
