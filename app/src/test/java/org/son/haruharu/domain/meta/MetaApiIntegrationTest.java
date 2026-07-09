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
    @DisplayName("인증 없이 GET /api/v1/meta/enums 호출 시 선택지형 enum이 code+label로 반환된다")
    void givenNoAuth_whenGetEnums_thenReturnsCodeLabelOptions() throws Exception {
        mockMvc.perform(get("/api/v1/meta/enums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.drinkingStatus[0].code").value("NONE"))
                .andExpect(jsonPath("$.data.drinkingStatus[0].label").value("안 마심"))
                .andExpect(jsonPath("$.data.smokingStatus[3].code").value("E_CIGARETTE"))
                .andExpect(jsonPath("$.data.smokingStatus[3].label").value("전자담배"));
    }
}
