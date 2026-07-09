package org.son.haruharu.domain.home;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("홈 조회 응답이 point_balance·matching·active_room(snake_case)으로 직렬화된다")
    void givenNewUser_whenGetHome_thenReturnsAggregatedSnakeCase() throws Exception {
        mockMvc.perform(get("/api/v1/home").with(authentication(auth(900L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.point_balance").value(0))
                .andExpect(jsonPath("$.data.matching.status").value("NONE"))
                .andExpect(jsonPath("$.data.matching.confirm_at").exists())
                .andExpect(jsonPath("$.data.matching.queued_at").value(nullValue()))
                .andExpect(jsonPath("$.data.active_room").value(nullValue()));
    }

    @Test
    @DisplayName("인증 없이 홈 조회 시 401을 반환한다")
    void givenNoAuth_whenGetHome_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
                .andExpect(status().isUnauthorized());
    }
}
