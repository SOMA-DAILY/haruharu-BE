package org.son.haruharu.domain.user.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.user.business.dto.OnboardingStatusResponse;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.business.exception.InactiveUserException;
import org.son.haruharu.domain.user.business.exception.OnboardingIncompleteException;
import org.son.haruharu.domain.value.business.ValueService;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserReader userReader;
    @Mock UserWriter userWriter;
    @Mock ProfileService profileService;
    @Mock IdentityService identityService;
    @Mock ValueService valueService;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-06-26 고정 → 1999-06-26생은 27세
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 6, 26, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userReader, userWriter, profileService, identityService, valueService, clock);
    }

    @Test
    @DisplayName("로그인한 유저가 내 프로필 조회 시 user_id·user_status·프로필·이미지를 반환한다")
    void givenLoggedInUser_whenGetMyProfile_thenReturnsAggregatedProfile() {
        Long userId = 1L;
        Long imageId = 1L;
        ProfileResponse profileResponse = new ProfileResponse(
            "테스터", "서울", "직장인",
            175, "INFP", DrinkingStatus.SOMETIMES, SmokingStatus.NONE,
            List.of(new ProfileResponse.ImageInfo(imageId, "https://image1.png", 0))
        );

        given(profileService.getProfile(userId)).willReturn(profileResponse);
        given(identityService.findVerified(userId))
            .willReturn(Optional.of(new VerifiedIdentity("MALE", LocalDate.of(1999, 6, 26))));

        UserProfileResponse result = userService.getMyProfile(userId);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.userStatus()).isEqualTo("VERIFIED");
        assertThat(result.profile().nickname()).isEqualTo("테스터");
        assertThat(result.profile().age()).isEqualTo(27);
        assertThat(result.images()).hasSize(1);
        assertThat(result.images().get(0).id()).isEqualTo(imageId);
        assertThat(result.images().get(0).sortedOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("미인증 유저 조회 시 user_status가 UNVERIFIED로 반환된다")
    void givenUnverifiedUser_whenGetMyProfile_thenUserStatusUnverified() {
        Long userId = 1L;
        given(profileService.getProfile(userId)).willReturn(new ProfileResponse(
            "유저", "서울", "학생", 160, "ENFP", DrinkingStatus.NONE, SmokingStatus.NONE, List.of()));
        // 미인증: findVerified가 empty → user_status UNVERIFIED

        UserProfileResponse result = userService.getMyProfile(userId);

        assertThat(result.userStatus()).isEqualTo("UNVERIFIED");
    }

    // ===================== completeOnboarding =====================

    @Test
    @DisplayName("프로필·가치관·이미지가 모두 완료된 ONBOARDING 유저가 온보딩 완료 시 ACTIVE로 전이된다")
    void givenCompletedData_whenCompleteOnboarding_thenTransitionsToActive() {
        Long userId = 1L;
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 1, 0, 0);
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ONBOARDING, createdAt));
        given(profileService.hasCompletedProfile(userId)).willReturn(true);
        given(valueService.hasAnswered(userId)).willReturn(true);
        given(identityService.findVerified(userId)).willReturn(Optional.empty());
        given(userWriter.save(any())).willReturn(new User(userId, UserStatus.ACTIVE, createdAt));

        OnboardingStatusResponse result = userService.completeOnboarding(userId);

        assertThat(result.userStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userWriter).should().save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(captor.getValue().createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("온보딩 완료 후 본인인증까지 되어 있으면 user_status가 VERIFIED로 반환된다")
    void givenVerifiedIdentity_whenCompleteOnboarding_thenReturnsVerified() {
        Long userId = 1L;
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ONBOARDING, LocalDateTime.now()));
        given(profileService.hasCompletedProfile(userId)).willReturn(true);
        given(valueService.hasAnswered(userId)).willReturn(true);
        given(identityService.findVerified(userId))
            .willReturn(Optional.of(new VerifiedIdentity("MALE", LocalDate.of(1999, 6, 26))));
        given(userWriter.save(any())).willReturn(new User(userId, UserStatus.ACTIVE, LocalDateTime.now()));

        OnboardingStatusResponse result = userService.completeOnboarding(userId);

        assertThat(result.userStatus()).isEqualTo("VERIFIED");
    }

    @Test
    @DisplayName("프로필이 미완인 유저가 온보딩 완료 시 예외가 발생하고 상태가 전이되지 않는다")
    void givenIncompleteProfile_whenCompleteOnboarding_thenThrows() {
        Long userId = 1L;
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ONBOARDING, LocalDateTime.now()));
        given(profileService.hasCompletedProfile(userId)).willReturn(false);

        assertThatThrownBy(() -> userService.completeOnboarding(userId))
            .isInstanceOf(OnboardingIncompleteException.class);
        then(userWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("가치관 답변이 없는 유저가 온보딩 완료 시 예외가 발생한다")
    void givenNoValueAnswers_whenCompleteOnboarding_thenThrows() {
        Long userId = 1L;
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ONBOARDING, LocalDateTime.now()));
        given(profileService.hasCompletedProfile(userId)).willReturn(true);
        given(valueService.hasAnswered(userId)).willReturn(false);

        assertThatThrownBy(() -> userService.completeOnboarding(userId))
            .isInstanceOf(OnboardingIncompleteException.class);
        then(userWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("차단(BANNED)된 유저가 온보딩 완료 호출 시 InactiveUserException이 발생하고 상태가 전이되지 않는다")
    void givenBannedUser_whenCompleteOnboarding_thenThrowsForbidden() {
        Long userId = 1L;
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.BANNED, LocalDateTime.now()));

        assertThatThrownBy(() -> userService.completeOnboarding(userId))
            .isInstanceOf(InactiveUserException.class);
        then(userWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("이미 ACTIVE인 유저가 온보딩 완료 시 재저장 없이 현재 상태를 반환한다(멱등)")
    void givenAlreadyActiveUser_whenCompleteOnboarding_thenIdempotentNoWrite() {
        Long userId = 1L;
        given(userReader.getById(userId)).willReturn(new User(userId, UserStatus.ACTIVE, LocalDateTime.now()));
        given(identityService.findVerified(userId)).willReturn(Optional.empty());

        OnboardingStatusResponse result = userService.completeOnboarding(userId);

        assertThat(result.userStatus()).isEqualTo("ACTIVE");
        then(userWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("로그인한 유저가 탈퇴 시 이미지·인증 정보 삭제 후 상태가 DELETED로 변경된다")
    void givenLoggedInUser_whenWithdraw_thenDeletesPiiAndChangesStatusToDeleted() {
        Long userId = 1L;
        User user = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());

        given(userReader.getById(userId)).willReturn(user);
        given(userWriter.save(any())).willReturn(new User(userId, UserStatus.DELETED, user.createdAt()));

        userService.withdraw(userId);

        then(profileService).should().deleteAllImages(userId);
        then(identityService).should().deleteIdentity(userId);
        then(userWriter).should().save(any());
    }
}
