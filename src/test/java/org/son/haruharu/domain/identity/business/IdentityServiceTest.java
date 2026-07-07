package org.son.haruharu.domain.identity.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.exception.DuplicateCiException;
import org.son.haruharu.domain.identity.business.exception.ReRegistrationBlockedException;
import org.son.haruharu.domain.identity.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock UserIdentityReader userIdentityReader;
    @Mock UserIdentityWriter userIdentityWriter;
    @Mock PassVerificationPort passVerificationPort;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 7, 7, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    IdentityService identityService;

    @BeforeEach
    void setUp() {
        identityService = new IdentityService(
            userIdentityReader, userIdentityWriter, passVerificationPort, clock
        );
    }

    @Test
    @DisplayName("이미 인증된 유저가 본인인증 요청 시 UserAlreadyVerifiedException이 발생한다")
    void givenAlreadyVerifiedUser_whenVerifyIdentity_thenThrowsUserAlreadyVerifiedException() {
        Long userId = 1L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("iv-token");

        given(userIdentityReader.existsByUserId(userId)).willReturn(true);

        assertThatThrownBy(() -> identityService.verifyIdentity(userId, command))
            .isInstanceOf(UserAlreadyVerifiedException.class);
    }

    @Test
    @DisplayName("미인증 유저가 유효한 인증 식별자로 요청 시 인증 정보가 저장된다")
    void givenUnverifiedUser_whenVerifyIdentityWithValidId_thenSavesIdentity() {
        Long userId = 2L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("iv-token");
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("iv-token")).willReturn(passResult);
        given(userIdentityReader.existsByCi("ci-value")).willReturn(false);

        identityService.verifyIdentity(userId, command);

        then(userIdentityWriter).should().save(any());
    }

    @Test
    @DisplayName("이미 다른 계정에 연결된 CI로 인증 요청 시 DuplicateCiException이 발생한다")
    void givenDuplicateCi_whenVerifyIdentity_thenThrowsDuplicateCiException() {
        Long userId = 3L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("iv-token");
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("iv-token")).willReturn(passResult);
        given(userIdentityReader.existsByCi("ci-value")).willReturn(true);

        assertThatThrownBy(() -> identityService.verifyIdentity(userId, command))
            .isInstanceOf(DuplicateCiException.class);
        then(userIdentityWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("탈퇴 후 30일 이내 동일 CI로 재인증 요청 시 ReRegistrationBlockedException이 발생한다")
    void givenRecentlyWithdrawnCi_whenVerifyIdentity_thenThrowsReRegistrationBlocked() {
        Long userId = 4L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("iv-token");
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("iv-token")).willReturn(passResult);
        given(userIdentityReader.existsByCi("ci-value")).willReturn(false);
        given(userIdentityReader.existsWithdrawnByCiWithin(eq("ci-value"), any())).willReturn(true);

        assertThatThrownBy(() -> identityService.verifyIdentity(userId, command))
            .isInstanceOf(ReRegistrationBlockedException.class);
        then(userIdentityWriter).should(never()).save(any());
    }
}
