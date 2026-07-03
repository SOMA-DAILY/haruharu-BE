package org.son.haruharu.domain.identity.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock UserIdentityReader userIdentityReader;
    @Mock UserIdentityWriter userIdentityWriter;
    @Mock PassVerificationPort passVerificationPort;

    IdentityService identityService;

    @BeforeEach
    void setUp() {
        identityService = new IdentityService(
            userIdentityReader, userIdentityWriter, passVerificationPort
        );
    }

    @Test
    @DisplayName("이미 인증된 유저가 본인인증 요청 시 UserAlreadyVerifiedException이 발생한다")
    void givenAlreadyVerifiedUser_whenVerifyIdentity_thenThrowsUserAlreadyVerifiedException() {
        Long userId = 1L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("pass-token");

        given(userIdentityReader.existsByUserId(userId)).willReturn(true);

        assertThatThrownBy(() -> identityService.verifyIdentity(userId, command))
            .isInstanceOf(UserAlreadyVerifiedException.class);
    }

    @Test
    @DisplayName("미인증 유저가 유효한 PASS 토큰으로 인증 요청 시 인증 정보가 저장된다")
    void givenUnverifiedUser_whenVerifyIdentityWithValidToken_thenSavesIdentity() {
        Long userId = 2L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("pass-token");
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("pass-token")).willReturn(passResult);

        identityService.verifyIdentity(userId, command);

        then(userIdentityWriter).should().save(any());
    }
}
