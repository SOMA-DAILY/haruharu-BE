package org.son.haruharu.domain.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProfileUpsertIntegrationTest {

    @Autowired ProfileService profileService;
    @Autowired UserProfileReader userProfileReader;
    @Autowired UserProfileJpaRepository jpaRepository;

    @Test
    @DisplayName("프로필이 없는 신규 유저가 PUT 시 새 프로필이 생성되고 id가 부여된다")
    void givenNoProfile_whenUpdateProfile_thenCreatesWithGeneratedId() {
        Long userId = 1L;
        UpdateProfileCommand command = new UpdateProfileCommand(
            "서울", "회사원",
            178, "ENFP", "하루", DrinkingStatus.SOMETIMES, SmokingStatus.NONE);

        profileService.updateProfile(userId, command);

        UserProfile saved = userProfileReader.getByUserId(userId);
        assertThat(saved.id()).isNotNull();          // @GeneratedValue(IDENTITY)가 id 생성 (persist 경로 검증)
        assertThat(saved.nickname()).isEqualTo("하루");
    }

    @Test
    @DisplayName("같은 유저가 두 번 PUT 해도 row는 1개로 유지되고 최신 값으로 갱신된다")
    void givenExistingProfile_whenUpdateAgain_thenUpsertsSingleRow() {
        Long userId = 2L;
        profileService.updateProfile(userId, new UpdateProfileCommand(
            "서울", "회사원",
            178, "ENFP", "하루", DrinkingStatus.SOMETIMES, SmokingStatus.NONE));

        profileService.updateProfile(userId, new UpdateProfileCommand(
            "부산", "학생",
            165, "INFJ", "이틀", DrinkingStatus.NONE, SmokingStatus.NONE));

        List<UserProfileEntity> rows = jpaRepository.findAll().stream()
            .filter(e -> e.toDomain().userId().equals(userId))
            .toList();
        assertThat(rows).hasSize(1);                 // upsert 계약: row 1개 유지 (중복 생성 없음)
        assertThat(rows.get(0).toDomain().nickname()).isEqualTo("이틀");
    }

    @Test
    @DisplayName("마이페이지 편집(음주·흡연 미전송)으로 PUT 시 온보딩에서 설정한 음주·흡연이 보존된다")
    void givenOmittedDrinkingSmoking_whenUpdate_thenPreservesExisting() {
        Long userId = 3L;
        // 온보딩: 음주·흡연 포함 저장
        profileService.updateProfile(userId, new UpdateProfileCommand(
            "서울", "회사원",
            178, "ENFP", "하루", DrinkingStatus.OFTEN, SmokingStatus.E_CIGARETTE));

        // 마이페이지 편집: 음주·흡연 미전송(null) → 기존 값 보존되어야 함
        profileService.updateProfile(userId, new UpdateProfileCommand(
            "부산", "학생",
            165, "INFJ", "이틀", null, null));

        UserProfile saved = userProfileReader.getByUserId(userId);
        assertThat(saved.nickname()).isEqualTo("이틀");        // 편집 필드는 갱신
        assertThat(saved.location()).isEqualTo("부산");
        assertThat(saved.drinkingStatus()).isEqualTo(DrinkingStatus.OFTEN);       // 미전송 → 보존
        assertThat(saved.smokingStatus()).isEqualTo(SmokingStatus.E_CIGARETTE);   // 미전송 → 보존
    }
}
