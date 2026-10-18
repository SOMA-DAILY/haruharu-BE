package org.son.haruharu.domain.identity.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.exception.DuplicateCiException;
import org.son.haruharu.domain.identity.business.exception.UnderageNotAllowedException;
import org.son.haruharu.domain.identity.business.event.IdentityVerifiedEvent;
import org.son.haruharu.domain.identity.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.mockito.ArgumentCaptor;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock UserIdentityReader userIdentityReader;
    @Mock UserIdentityWriter userIdentityWriter;
    @Mock PassVerificationPort passVerificationPort;
    @Mock ApplicationEventPublisher eventPublisher;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 7, 7, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    IdentityService identityService;

    @BeforeEach
    void setUp() {
        identityService = new IdentityService(
            userIdentityReader, userIdentityWriter, passVerificationPort, eventPublisher, clock
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
    @DisplayName("본인인증을 저장하면 IdentityVerifiedEvent 를 발행한다(커밋 후 기수 신청 판정의 트리거, #512)")
    void givenUnverifiedUser_whenVerifyIdentity_thenPublishesEventAfterSave() {
        Long userId = 2L;
        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("iv-token")).willReturn(new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        ));
        given(userIdentityReader.existsByCi("ci-value")).willReturn(false);

        identityService.verifyIdentity(userId, new VerifyIdentityCommand("iv-token"));

        var order = inOrder(userIdentityWriter, eventPublisher);
        order.verify(userIdentityWriter).save(any());
        order.verify(eventPublisher).publishEvent(new IdentityVerifiedEvent(userId));
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
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("만 19세 미만이면 UnderageNotAllowedException(403)이 발생하고 인증 정보가 저장되지 않는다")
    void givenUnderageBirthday_whenVerifyIdentity_thenThrowsAndNotSaved() {
        Long userId = 5L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("iv-token");
        // 고정 시각 2026-07-07 기준: 2007-07-08생은 내일이 19번째 생일 → 만 18세
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(2007, 7, 8), "MALE", "홍길동", "SKT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("iv-token")).willReturn(passResult);

        assertThatThrownBy(() -> identityService.verifyIdentity(userId, command))
            .isInstanceOf(UnderageNotAllowedException.class);
        then(userIdentityWriter).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("오늘이 19번째 생일이면 만 19세로 판정되어 인증이 통과된다(경계)")
    void givenNineteenthBirthdayToday_whenVerifyIdentity_thenSaves() {
        Long userId = 6L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("iv-token");
        // 고정 시각 2026-07-07 기준: 2007-07-07생은 오늘이 19번째 생일 → 만 19세
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(2007, 7, 7), "FEMALE", "홍길순", "KT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("iv-token")).willReturn(passResult);
        given(userIdentityReader.existsByCi("ci-value")).willReturn(false);

        identityService.verifyIdentity(userId, command);

        then(userIdentityWriter).should().save(any());
    }

    @Test
    @DisplayName("탈퇴 직후 동일 CI로 재인증 요청 시 차단 없이 인증되고 탈퇴 잔존 행은 파기하지 않는다")
    void givenJustWithdrawnCi_whenVerifyIdentity_thenVerifiedWithoutPurge() {
        Long userId = 4L;
        VerifyIdentityCommand command = new VerifyIdentityCommand("iv-token");
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("iv-token")).willReturn(passResult);
        given(userIdentityReader.existsByCi("ci-value")).willReturn(false);

        identityService.verifyIdentity(userId, command);

        // partial unique(WHERE deleted_at IS NULL) 덕에 파기 없이 저장된다 —
        // 탈퇴 이력(CI)은 보존되어야 신고·제재 추적이 가능하다(#245).
        then(userIdentityWriter).should().save(any());
    }
}
