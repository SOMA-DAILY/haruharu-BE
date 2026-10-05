package org.son.haruharu.domain.meta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetaApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("/api/v1/meta/enums는 제거되어 404를 반환한다 — enum 코드·유효성은 Swagger 스키마가 커버(#210)")
    void givenRemovedEndpoint_whenGetEnums_thenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/meta/enums"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("인증 없이 메타를 조회할 수 있고 폐기된 자동매칭 정책은 응답에 포함되지 않는다")
    void givenNoAuth_whenGetMeta_thenOmitsAutoMatchingPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.matching").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/meta 응답에 세션 정책 상수 session.day_start_hour(9)·total_days(3)가 포함된다")
    void givenNoAuth_whenGetMeta_thenReturnsSessionConstants() throws Exception {
        mockMvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.day_start_hour").value(9))
                .andExpect(jsonPath("$.data.session.total_days").value(3));
    }
}
