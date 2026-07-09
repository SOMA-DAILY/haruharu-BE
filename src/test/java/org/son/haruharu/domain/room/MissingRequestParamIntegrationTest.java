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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 필수 쿼리 파라미터 누락(MissingServletRequestParameterException)이 500이 아닌 400으로 처리되는지 검증한다(#159). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissingRequestParamIntegrationTest {

    @Autowired MockMvc mockMvc;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("필수 쿼리 파라미터(day) 누락 시 400과 MISSING_PARAMETER 코드를 반환한다")
    void givenMissingDayParam_whenGetFeed_thenBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/1/feed")
                        .with(authentication(auth(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("MISSING_PARAMETER"));
    }
}
