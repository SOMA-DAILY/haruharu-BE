package org.son.haruharu.domain.notification;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationSettingApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("설정이 없으면 기본값 push_enabled=true로 응답한다")
    void givenNoSetting_whenGet_thenDefaultOn() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/notification-settings")
                        .with(authentication(auth(910L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.push_enabled").value(true));
    }

    @Test
    @DisplayName("off로 변경하면 이후 조회에서 push_enabled=false가 유지된다")
    void givenPutOff_whenGet_thenOffPersisted() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/notification-settings")
                        .with(authentication(auth(911L)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"push_enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.push_enabled").value(false));

        mockMvc.perform(get("/api/v1/users/me/notification-settings")
                        .with(authentication(auth(911L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.push_enabled").value(false));
    }

    @Test
    @DisplayName("push_enabled 누락 시 400을 반환한다")
    void givenMissingField_whenPut_thenBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/notification-settings")
                        .with(authentication(auth(912L)))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 없이 접근하면 401을 반환한다")
    void givenNoAuth_whenGet_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/notification-settings"))
                .andExpect(status().isUnauthorized());
    }
}
