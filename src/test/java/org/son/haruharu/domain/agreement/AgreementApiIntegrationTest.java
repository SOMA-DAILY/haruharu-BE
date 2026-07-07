package org.son.haruharu.domain.agreement;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.agreement.implement.AgreementType;
import org.son.haruharu.domain.agreement.storage.UserAgreementEntity;
import org.son.haruharu.domain.agreement.storage.UserAgreementJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgreementApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserAgreementJpaRepository userAgreementJpaRepository;

    @BeforeEach
    void clean() {
        userAgreementJpaRepository.deleteAll();
    }

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("필수+마케팅 약관 동의를 저장하면 200이고 이력이 저장된다")
    void givenAllAgreements_whenPost_thenSaves() throws Exception {
        Long userId = 1L;
        String body = """
                { "agreements": [
                  { "type": "SERVICE_TERMS", "version": "2026-07-05", "agreed": true },
                  { "type": "PRIVACY", "version": "2026-07-05", "agreed": true },
                  { "type": "AGE_19", "version": "2026-07-05", "agreed": true },
                  { "type": "MARKETING", "version": "2026-07-05", "agreed": false }
                ] }
                """;

        mockMvc.perform(post("/api/v1/users/me/agreements")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        assertThat(userAgreementJpaRepository.findAll())
                .extracting(e -> e.toDomain().type())
                .containsExactlyInAnyOrder(
                    AgreementType.SERVICE_TERMS, AgreementType.PRIVACY, AgreementType.AGE_19, AgreementType.MARKETING);
    }

    @Test
    @DisplayName("필수 약관 누락 시 400 REQUIRED_AGREEMENT_MISSING을 반환하고 저장되지 않는다")
    void givenMissingRequired_whenPost_thenReturns400() throws Exception {
        String body = """
                { "agreements": [
                  { "type": "SERVICE_TERMS", "version": "2026-07-05", "agreed": true },
                  { "type": "PRIVACY", "version": "2026-07-05", "agreed": true }
                ] }
                """;

        mockMvc.perform(post("/api/v1/users/me/agreements")
                        .with(authentication(auth(1L)))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUIRED_AGREEMENT_MISSING"));

        assertThat(userAgreementJpaRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("마케팅 동의 저장 후 마케팅 동의 조회 시 agreed=true를 반환한다")
    void givenMarketingAgreed_whenGetMarketing_thenReturnsAgreed() throws Exception {
        Long userId = 1L;
        String body = """
                { "agreements": [
                  { "type": "SERVICE_TERMS", "version": "2026-07-05", "agreed": true },
                  { "type": "PRIVACY", "version": "2026-07-05", "agreed": true },
                  { "type": "AGE_19", "version": "2026-07-05", "agreed": true },
                  { "type": "MARKETING", "version": "2026-07-05", "agreed": true }
                ] }
                """;
        mockMvc.perform(post("/api/v1/users/me/agreements")
                        .with(authentication(auth(userId)))
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/agreements/marketing")
                        .with(authentication(auth(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agreed").value(true))
                .andExpect(jsonPath("$.data.version").value("2026-07-05"));
    }
}
