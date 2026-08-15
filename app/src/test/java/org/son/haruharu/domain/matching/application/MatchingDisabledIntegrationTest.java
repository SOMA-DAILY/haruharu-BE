package org.son.haruharu.domain.matching.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 자동매칭 비활성(#308) 검증. haruharu.matching.enabled 기본 false라 MatchingController 빈이 등록되지 않아
 * 매칭 대기열 엔드포인트가 사라진다(핸들러 없음 → 404). ACTIVE 유저 토큰으로 접근해 필터는 통과시키되
 * 핸들러가 없어 404가 나는지 확인한다(필터가 막으면 403이므로 404여야 "핸들러 부재 = 컨트롤러 비활성"이 증명된다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchingDisabledIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserJpaRepository userJpaRepository;
    @Autowired
    JwtProvider jwtProvider;

    @BeforeEach
    void clean() {
        userJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("자동매칭 비활성 시 매칭 대기열 엔드포인트가 없어 ACTIVE 유저도 404를 받는다")
    void givenMatchingDisabled_whenActiveUserCallsQueue_thenNotFound() throws Exception {
        // given
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String token = jwtProvider.generateAccessToken(userId);

        // when & then
        mockMvc.perform(get("/api/v1/matching/queue")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
