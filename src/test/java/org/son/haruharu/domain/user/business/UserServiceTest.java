package org.son.haruharu.domain.user.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserReader userReader;
    @Mock UserWriter userWriter;
    @Mock ProfileService profileService;
    @Mock IdentityService identityService;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userReader, userWriter, profileService, identityService);
    }

    @Test
    @DisplayName("로그인한 유저가 내 프로필 조회 시 user_id·user_status·프로필·이미지를 반환한다")
    void givenLoggedInUser_whenGetMyProfile_thenReturnsAggregatedProfile() {
        UUID userId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        ProfileResponse profileResponse = new ProfileResponse(
            "테스터", "MALE", 27, "서울", "직장인",
            175, "INFP", "가끔", "비흡연", "무교",
            List.of(new ProfileResponse.ImageInfo(imageId, "https://image1.png", 0))
        );

        given(profileService.getProfile(userId)).willReturn(profileResponse);
        given(identityService.isVerified(userId)).willReturn(true);

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
        UUID userId = UUID.randomUUID();
        given(profileService.getProfile(userId)).willReturn(new ProfileResponse(
            "유저", "FEMALE", 25, "서울", "학생", 160, "ENFP", "안함", "비흡연", "무교", List.of()));
        given(identityService.isVerified(userId)).willReturn(false);

        UserProfileResponse result = userService.getMyProfile(userId);

        assertThat(result.userStatus()).isEqualTo("UNVERIFIED");
    }

    @Test
    @DisplayName("로그인한 유저가 탈퇴 시 이미지·인증 정보 삭제 후 상태가 DELETED로 변경된다")
    void givenLoggedInUser_whenWithdraw_thenDeletesPiiAndChangesStatusToDeleted() {
        UUID userId = UUID.randomUUID();
        User user = new User(userId, UserStatus.ACTIVE, LocalDateTime.now());

        given(userReader.getById(userId)).willReturn(user);
        given(userWriter.save(any())).willReturn(new User(userId, UserStatus.DELETED, user.createdAt()));

        userService.withdraw(userId);

        then(profileService).should().deleteAllImages(userId);
        then(identityService).should().deleteIdentity(userId);
        then(userWriter).should().save(any());
    }
}
