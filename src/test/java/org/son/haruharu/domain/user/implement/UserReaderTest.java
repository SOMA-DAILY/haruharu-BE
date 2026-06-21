package org.son.haruharu.domain.user.implement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.user.storage.exception.UserNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserReaderTest {

    @Mock
    private UserRepository userRepository;

    private UserReader userReader;

    @BeforeEach
    void setUp() {
        userReader = new UserReader(userRepository);
    }

    @Test
    @DisplayName("존재하는 유저 ID로 조회 시 User를 반환한다")
    void givenExistingUserId_whenGetById_thenReturnsUser() {
        var userId = UUID.randomUUID();
        var user = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        var result = userReader.getById(userId);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 UserNotFoundException이 발생한다")
    void givenNonExistentId_whenGetById_thenThrowsUserNotFoundException() {
        var unknownId = UUID.randomUUID();
        given(userRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userReader.getById(unknownId))
                .isInstanceOf(UserNotFoundException.class);
    }
}
