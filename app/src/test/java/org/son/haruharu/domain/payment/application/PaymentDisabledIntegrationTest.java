package org.son.haruharu.domain.payment.application;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 결제(IAP) 비활성(#309) 검증. haruharu.payment.enabled 기본 false라 IapWebhookController·IapProductController 빈 미등록.
 * webhook은 permitAll이라 인증 없이 접근해도 핸들러가 없어 404, 티켓 상품 조회도 ACTIVE 유저에게 404.
 */
@SpringBootTest(properties = "haruharu.payment.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentDisabledIntegrationTest {

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
    @DisplayName("결제 비활성 시 IAP webhook 엔드포인트가 없어 404를 받는다")
    void givenPaymentDisabled_whenWebhookCalled_thenNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/iap/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("결제 비활성 시 티켓 상품 조회 엔드포인트가 없어 ACTIVE 유저도 404를 받는다")
    void givenPaymentDisabled_whenActiveUserCallsProducts_thenNotFound() throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String token = jwtProvider.generateAccessToken(userId);

        mockMvc.perform(get("/api/v1/ticket/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
