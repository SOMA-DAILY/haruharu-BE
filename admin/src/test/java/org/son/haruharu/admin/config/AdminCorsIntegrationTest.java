package org.son.haruharu.admin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCorsIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("화이트리스트 오리진의 프리플라이트(OPTIONS)는 200 + Access-Control-Allow-Origin을 반환한다")
    void preflight_allowedOrigin_returnsCorsHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/admin/dashboard/summary")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    @DisplayName("화이트리스트에 없는 오리진의 프리플라이트는 차단된다(403)")
    void preflight_disallowedOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/v1/admin/dashboard/summary")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
