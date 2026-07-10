package org.son.haruharu.admin.domain.account.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.account.business.command.AdminLoginCommand;
import org.son.haruharu.admin.domain.account.business.dto.AdminMeResponse;
import org.son.haruharu.admin.domain.account.business.dto.AdminTokenResponse;
import org.son.haruharu.admin.domain.account.business.exception.AdminLoginFailedException;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountReader;
import org.son.haruharu.admin.domain.account.implement.AdminAccountStatus;
import org.son.haruharu.admin.domain.account.implement.AdminAccountWriter;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.admin.security.AdminJwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock AdminAccountReader adminAccountReader;
    @Mock AdminAccountWriter adminAccountWriter;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AdminJwtProvider adminJwtProvider;

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);

    AdminAuthService service;

    private static final String EMAIL = "admin@haruharu.app";
    private static final String RAW_PW = "P@ssw0rd!";
    private static final String HASH = "$2a$10$hashed";

    @BeforeEach
    void setUp() {
        service = new AdminAuthService(adminAccountReader, adminAccountWriter, passwordEncoder, adminJwtProvider, clock);
    }

    private AdminAccount account(AdminAccountStatus status) {
        return new AdminAccount(1L, EMAIL, HASH, "슈퍼관리자", AdminRole.SUPER, status, null);
    }

    @Test
    @DisplayName("올바른 자격증명이면 role claim이 담긴 토큰 쌍을 발급하고 마지막 로그인 시각을 갱신한다")
    void givenValidCredentials_whenLogin_thenIssuesTokensAndUpdatesLastLogin() {
        given(adminAccountReader.findByEmail(EMAIL)).willReturn(Optional.of(account(AdminAccountStatus.ACTIVE)));
        given(passwordEncoder.matches(RAW_PW, HASH)).willReturn(true);
        given(adminAccountWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(adminJwtProvider.generateAccessToken(1L, AdminRole.SUPER)).willReturn("access-token");
        given(adminJwtProvider.generateRefreshToken(1L, AdminRole.SUPER)).willReturn("refresh-token");

        AdminTokenResponse result = service.login(new AdminLoginCommand(EMAIL, RAW_PW));

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(adminAccountWriter).save(captor.capture());
        assertThat(captor.getValue().lastLoginAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 AdminLoginFailedException(401)")
    void givenUnknownEmail_whenLogin_thenThrows() {
        given(adminAccountReader.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new AdminLoginCommand(EMAIL, RAW_PW)))
                .isInstanceOf(AdminLoginFailedException.class);
        verify(adminAccountWriter, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호 불일치면 AdminLoginFailedException(401), 토큰 미발급")
    void givenWrongPassword_whenLogin_thenThrows() {
        given(adminAccountReader.findByEmail(EMAIL)).willReturn(Optional.of(account(AdminAccountStatus.ACTIVE)));
        given(passwordEncoder.matches(RAW_PW, HASH)).willReturn(false);

        assertThatThrownBy(() -> service.login(new AdminLoginCommand(EMAIL, RAW_PW)))
                .isInstanceOf(AdminLoginFailedException.class);
        verify(adminJwtProvider, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("INACTIVE 계정은 비밀번호가 맞아도 로그인 불가(401)")
    void givenInactiveAccount_whenLogin_thenThrows() {
        given(adminAccountReader.findByEmail(EMAIL)).willReturn(Optional.of(account(AdminAccountStatus.INACTIVE)));

        assertThatThrownBy(() -> service.login(new AdminLoginCommand(EMAIL, RAW_PW)))
                .isInstanceOf(AdminLoginFailedException.class);
        verify(adminJwtProvider, never()).generateAccessToken(any(), any());
    }

    @Test
    @DisplayName("내 정보 조회는 계정 id로 조회해 이메일·역할을 반환한다")
    void givenAdminId_whenGetMe_thenReturnsAccount() {
        given(adminAccountReader.getById(1L)).willReturn(account(AdminAccountStatus.ACTIVE));

        AdminMeResponse result = service.getMe(1L);

        assertThat(result.adminId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.role()).isEqualTo(AdminRole.SUPER);
    }
}
