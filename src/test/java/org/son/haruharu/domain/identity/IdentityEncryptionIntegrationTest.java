package org.son.haruharu.domain.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.exception.DuplicateCiException;
import org.son.haruharu.domain.identity.storage.UserIdentityJpaRepository;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class IdentityEncryptionIntegrationTest {

    @Autowired IdentityService identityService;
    @Autowired UserIdentityJpaRepository jpaRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PassVerificationPort passVerificationPort;

    private static final String CI = "ci-plain-value-1234567890";

    @BeforeEach
    void clean() {
        jpaRepository.deleteAll();
    }

    private PassVerificationResult passResult() {
        return new PassVerificationResult(
                CI, "di-plain-value", "010-1234-5678",
                LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        );
    }

    @Test
    @DisplayName("본인인증 저장 시 CI/DI가 DB에 평문이 아닌 암호문으로 저장된다")
    void givenVerification_whenSaved_thenCiDiStoredEncrypted() {
        given(passVerificationPort.verify("iv-token")).willReturn(passResult());

        identityService.verifyIdentity(1L, new VerifyIdentityCommand("iv-token"));

        String rawCi = jdbcTemplate.queryForObject(
                "SELECT ci FROM user_identities WHERE user_id = 1", String.class);
        String rawDi = jdbcTemplate.queryForObject(
                "SELECT di FROM user_identities WHERE user_id = 1", String.class);

        assertThat(rawCi).isNotEqualTo(CI);
        assertThat(rawDi).isNotEqualTo("di-plain-value");
    }

    @Test
    @DisplayName("복호화하여 조회하면 원본 CI/DI 값이 복원된다")
    void givenEncryptedIdentity_whenRead_thenDecryptedToOriginal() {
        given(passVerificationPort.verify("iv-token")).willReturn(passResult());

        identityService.verifyIdentity(2L, new VerifyIdentityCommand("iv-token"));

        var identity = jpaRepository.findByUserId(2L).orElseThrow().toDomain();
        assertThat(identity.ci()).isEqualTo(CI);
        assertThat(identity.di()).isEqualTo("di-plain-value");
    }

    @Test
    @DisplayName("이미 인증된 CI로 다른 유저가 요청하면 blind index로 감지해 DuplicateCiException이 발생한다")
    void givenExistingCi_whenAnotherUserVerifies_thenThrowsDuplicateCi() {
        given(passVerificationPort.verify("iv-token")).willReturn(passResult());
        identityService.verifyIdentity(10L, new VerifyIdentityCommand("iv-token"));

        assertThatThrownBy(() ->
                identityService.verifyIdentity(11L, new VerifyIdentityCommand("iv-token")))
                .isInstanceOf(DuplicateCiException.class);
    }
}
