package org.son.haruharu.demo;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DemoModeInterceptor가 /api/v1/demo/** 전 경로에 데모 인가를 강제하는지 검증.
 * 실제 데모 엔드포인트(#265~)는 아직 없으므로 테스트 전용 핑 컨트롤러로 게이트만 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DemoGateIntegrationTest.DemoPingController.class)
class DemoGateIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @RestController
    static class DemoPingController {
        @GetMapping("/api/v1/demo/__ping")
        ApiResponse<String> ping() {
            return ApiResponse.ok("pong");
        }
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM users");
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private Long saveUser(boolean demoMode) {
        return userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null, demoMode)))
                .toDomain().id();
    }

    @Test
    @DisplayName("데모 계정은 /api/v1/demo/** 접근이 허용된다(200)")
    void givenDemoUser_whenAccessDemoPath_thenOk() throws Exception {
        Long userId = saveUser(true);

        mockMvc.perform(get("/api/v1/demo/__ping").with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    @DisplayName("일반 계정은 /api/v1/demo/** 접근 시 403 DEMO_FORBIDDEN")
    void givenNonDemoUser_whenAccessDemoPath_thenForbidden() throws Exception {
        Long userId = saveUser(false);

        mockMvc.perform(get("/api/v1/demo/__ping").with(authentication(auth(userId))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.error.code").value("DEMO_FORBIDDEN"));
    }

    @Test
    @DisplayName("미인증 요청은 /api/v1/demo/** 접근 시 401")
    void givenNoAuth_whenAccessDemoPath_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/demo/__ping"))
                .andExpect(status().isUnauthorized());
    }
}
