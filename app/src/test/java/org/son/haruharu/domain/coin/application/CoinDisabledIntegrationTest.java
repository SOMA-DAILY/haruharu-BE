package org.son.haruharu.domain.coin.application;

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
 * 코인 비활성(#309) 검증. haruharu.coin.enabled 기본 false라 CoinController 빈 미등록 → 코인 조회 엔드포인트가 사라진다.
 * ACTIVE 유저 토큰으로 접근해 상태 필터는 통과하되(403 아님) 핸들러가 없어 404가 나는지 확인한다.
 */
@SpringBootTest(properties = "haruharu.coin.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoinDisabledIntegrationTest {

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
    @DisplayName("코인 비활성 시 코인 지갑 조회 엔드포인트가 없어 ACTIVE 유저도 404를 받는다")
    void givenCoinDisabled_whenActiveUserCallsWallet_thenNotFound() throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String token = jwtProvider.generateAccessToken(userId);

        mockMvc.perform(get("/api/v1/coin/wallet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
