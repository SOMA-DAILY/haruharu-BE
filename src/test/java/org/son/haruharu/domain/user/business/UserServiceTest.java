package org.son.haruharu.domain.user.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserImageWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.user.business.command.UpdateImagesCommand;
import org.son.haruharu.domain.user.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.user.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserReader userReader;
    @Mock UserWriter userWriter;
    @Mock UserProfileReader userProfileReader;
    @Mock UserProfileWriter userProfileWriter;
    @Mock UserImageReader userImageReader;
    @Mock UserImageWriter userImageWriter;
    @Mock UserIdentityReader userIdentityReader;
    @Mock UserIdentityWriter userIdentityWriter;
    @Mock PassVerificationPort passVerificationPort;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userReader, userWriter,
            userProfileReader, userProfileWriter,
            userImageReader, userImageWriter,
            userIdentityReader, userIdentityWriter,
            passVerificationPort
        );
    }

    // ===================== getMyProfile =====================

    @Test
    @DisplayName("로그인한 유저가 내 프로필 조회 시 프로필 정보를 반환한다")
    void givenLoggedInUser_whenGetMyProfile_thenReturnsProfileInfo() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = new UserProfile(UUID.randomUUID(), userId,
            "MALE", LocalDate.of(1998, 5, 15), "서울", "직장인",
            175, "INFP", "테스터", "가끔", "비흡연", "무교", "잔잔한");
        List<UserImage> images = List.of(
            new UserImage(UUID.randomUUID(), userId, "https://image1.png", 0)
        );

        given(userProfileReader.getByUserId(userId)).willReturn(profile);
        given(userImageReader.getAllByUserId(userId)).willReturn(images);
        given(userIdentityReader.existsByUserId(userId)).willReturn(true);

        UserProfileResponse result = userService.getMyProfile(userId);

        assertThat(result.nickname()).isEqualTo("테스터");
        assertThat(result.imageUrls()).hasSize(1);
        assertThat(result.isIdentityVerified()).isTrue();
    }

    // ===================== updateProfile =====================

    @Test
    @DisplayName("로그인한 유저가 프로필 수정 시 저장 후 업데이트된 프로필을 반환한다")
    void givenLoggedInUser_whenUpdateProfile_thenSavesAndReturnsUpdatedProfile() {
        UUID userId = UUID.randomUUID();
        UpdateProfileCommand command = new UpdateProfileCommand(
            "MALE", LocalDate.of(1998, 5, 15), "서울", "직장인",
            175, "INFP", "새닉네임", "가끔", "비흡연", "무교", "잔잔한"
        );
        UserProfile existing = new UserProfile(UUID.randomUUID(), userId,
            "MALE", LocalDate.of(1998, 5, 15), "부산", "학생",
            175, "INFP", "기존닉", "안함", "비흡연", "무교", "활발한");
        UserProfile updated = command.applyTo(existing);
        List<UserImage> images = List.of();

        given(userProfileReader.getByUserId(userId)).willReturn(existing);
        given(userProfileWriter.save(any())).willReturn(updated);
        given(userImageReader.getAllByUserId(userId)).willReturn(images);
        given(userIdentityReader.existsByUserId(userId)).willReturn(false);

        UserProfileResponse result = userService.updateProfile(userId, command);

        assertThat(result.nickname()).isEqualTo("새닉네임");
        then(userProfileWriter).should().save(any());
    }

    // ===================== updateImages =====================

    @Test
    @DisplayName("로그인한 유저가 이미지 업데이트 시 기존 이미지를 전체 삭제하고 새 이미지를 저장한다")
    void givenLoggedInUser_whenUpdateImages_thenDeletesOldAndSavesNew() {
        UUID userId = UUID.randomUUID();
        UpdateImagesCommand command = new UpdateImagesCommand(
            List.of("https://new1.png", "https://new2.png")
        );

        userService.updateImages(userId, command);

        then(userImageWriter).should().deleteAllByUserId(userId);
        then(userImageWriter).should(times(2)).save(any());
    }

    // ===================== verifyIdentity =====================

    @Test
    @DisplayName("이미 인증된 유저가 본인인증 요청 시 UserAlreadyVerifiedException이 발생한다")
    void givenAlreadyVerifiedUser_whenVerifyIdentity_thenThrowsUserAlreadyVerifiedException() {
        UUID userId = UUID.randomUUID();
        VerifyIdentityCommand command = new VerifyIdentityCommand("pass-token");

        given(userIdentityReader.existsByUserId(userId)).willReturn(true);

        assertThatThrownBy(() -> userService.verifyIdentity(userId, command))
            .isInstanceOf(UserAlreadyVerifiedException.class);
    }

    @Test
    @DisplayName("미인증 유저가 본인인증 요청 시 인증 정보가 저장된다")
    void givenUnverifiedUser_whenVerifyIdentity_thenSavesIdentity() {
        UUID userId = UUID.randomUUID();
        VerifyIdentityCommand command = new VerifyIdentityCommand("pass-token");
        PassVerificationResult passResult = new PassVerificationResult(
            "ci-value", "di-value", "010-1234-5678",
            LocalDate.of(1998, 5, 15), "MALE", "홍길동", "SKT", false
        );

        given(userIdentityReader.existsByUserId(userId)).willReturn(false);
        given(passVerificationPort.verify("pass-token")).willReturn(passResult);

        userService.verifyIdentity(userId, command);

        then(userIdentityWriter).should().save(any());
    }

    // ===================== withdraw =====================

    @Test
    @DisplayName("로그인한 유저가 탈퇴 시 유저 상태가 DELETED로 변경된다")
    void givenLoggedInUser_whenWithdraw_thenUserStatusChangedToDeleted() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());

        given(userReader.getById(userId)).willReturn(user);
        given(userWriter.save(any())).willReturn(new User(userId, UserStatus.DELETED, user.createdAt()));

        userService.withdraw(userId);

        then(userWriter).should().save(any());
    }
}
