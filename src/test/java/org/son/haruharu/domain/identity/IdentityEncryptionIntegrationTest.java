package org.son.haruharu.domain.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.exception.DuplicateCiException;
import org.son.haruharu.domain.identity.business.exception.ReRegistrationBlockedException;
import org.son.haruharu.domain.identity.storage.UserIdentityJpaRepository;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class IdentityEncryptionIntegrationTest {

    @Autowired IdentityService identityService;
    @Autowired UserIdentityJpaRepository jpaRepository;
    @Autowired org.son.haruharu.domain.identity.implement.UserIdentityWriter userIdentityWriter;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean PassVerificationPort passVerificationPort;

    private static final String CI = "ci-plain-value-1234567890";

    @BeforeEach
    void clean() {
        // BaseEntity @SoftDelete 전역 필터로 deleteAll()이 soft-delete가 되어 물리 행이 남는다.
        // 비트랜잭션 테스트의 격리는 native 물리삭제로 복원한다.
        jdbcTemplate.update("DELETE FROM user_identities");
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

    @Test
    @DisplayName("탈퇴(soft) 후 30일 이내 동일 CI로 재인증하면 ReRegistrationBlockedException이 발생한다")
    void givenWithdrawnWithin30Days_whenReverifySameCi_thenBlocked() {
        given(passVerificationPort.verify("iv-token")).willReturn(passResult());
        identityService.verifyIdentity(100L, new VerifyIdentityCommand("iv-token"));
        identityService.deleteIdentity(100L); // soft-delete → deleted_at = now

        assertThatThrownBy(() ->
                identityService.verifyIdentity(101L, new VerifyIdentityCommand("iv-token")))
                .isInstanceOf(ReRegistrationBlockedException.class);
    }

    @Test
    @DisplayName("탈퇴 후 30일이 지난 동일 CI는 재인증이 허용되고 잔존 행이 파기된 뒤 새로 저장된다")
    void givenWithdrawnOver30Days_whenReverifySameCi_thenAllowedAndPurged() {
        given(passVerificationPort.verify("iv-token")).willReturn(passResult());
        identityService.verifyIdentity(200L, new VerifyIdentityCommand("iv-token"));
        identityService.deleteIdentity(200L);
        // deleted_at을 31일 전으로 조정해 차단 기간 경과를 시뮬레이션 (native — soft-delete 필터 우회)
        jdbcTemplate.update("UPDATE user_identities SET deleted_at = ? WHERE user_id = 200",
                Timestamp.valueOf(LocalDateTime.now().minusDays(31)));

        identityService.verifyIdentity(201L, new VerifyIdentityCommand("iv-token"));

        assertThat(jpaRepository.findByUserId(201L)).isPresent();
        // 잔존(탈퇴) 행은 물리 파기되어 남지 않는다
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_identities WHERE user_id = 200", Long.class);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("보관기간(30일)이 지난 탈퇴 본인인증은 배치 파기로 물리 삭제된다")
    void givenExpiredWithdrawnIdentity_whenPurgeBatch_thenPhysicallyDeleted() {
        given(passVerificationPort.verify("iv-token")).willReturn(passResult());
        identityService.verifyIdentity(300L, new VerifyIdentityCommand("iv-token"));
        identityService.deleteIdentity(300L);
        // deleted_at을 31일 전으로 조정 (보관기간 경과)
        jdbcTemplate.update("UPDATE user_identities SET deleted_at = ? WHERE user_id = 300",
                Timestamp.valueOf(LocalDateTime.now().minusDays(31)));

        userIdentityWriter.purgeWithdrawnBefore(LocalDateTime.now().minusDays(30));

        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_identities WHERE user_id = 300", Long.class);
        assertThat(remaining).isZero();
    }
}
