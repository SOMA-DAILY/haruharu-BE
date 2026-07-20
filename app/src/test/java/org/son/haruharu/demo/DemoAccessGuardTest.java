package org.son.haruharu.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.demo.exception.DemoForbiddenException;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DemoAccessGuardTest {

    @Mock UserReader userReader;

    DemoAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DemoAccessGuard(userReader);
    }

    private static Authentication auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("데모 계정이 아니면 DemoForbiddenException(403)")
    void givenNonDemoUser_whenRequireDemoAccount_thenThrows() {
        Long userId = 1L;
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ACTIVE, null, false));

        assertThatThrownBy(() -> guard.requireDemoAccount(auth(userId)))
                .isInstanceOf(DemoForbiddenException.class);
    }

    @Test
    @DisplayName("데모 계정이면 통과한다")
    void givenDemoUser_whenRequireDemoAccount_thenPasses() {
        Long userId = 1L;
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ACTIVE, null, true));

        assertThatCode(() -> guard.requireDemoAccount(auth(userId))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("인증이 없으면 fail-closed로 403")
    void givenNullAuthentication_whenRequireDemoAccount_thenThrows() {
        assertThatThrownBy(() -> guard.requireDemoAccount(null))
                .isInstanceOf(DemoForbiddenException.class);
    }
}
