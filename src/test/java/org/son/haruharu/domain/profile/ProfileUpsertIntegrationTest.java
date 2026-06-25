package org.son.haruharu.domain.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.storage.UserProfileEntity;
import org.son.haruharu.domain.profile.storage.UserProfileJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
        UUID userId = UUID.randomUUID();
        UpdateProfileCommand command = new UpdateProfileCommand(
            "MALE", LocalDate.of(1995, 3, 1), "서울", "회사원",
            178, "ENFP", "하루", "가끔", "비흡연", "무교");

        profileService.updateProfile(userId, command);

        UserProfile saved = userProfileReader.getByUserId(userId);
        assertThat(saved.id()).isNotNull();          // @GeneratedValue(UUID)가 id 생성 (persist 경로 검증)
        assertThat(saved.nickname()).isEqualTo("하루");
    }

    @Test
    @DisplayName("같은 유저가 두 번 PUT 해도 row는 1개로 유지되고 최신 값으로 갱신된다")
    void givenExistingProfile_whenUpdateAgain_thenUpsertsSingleRow() {
        UUID userId = UUID.randomUUID();
        profileService.updateProfile(userId, new UpdateProfileCommand(
            "MALE", LocalDate.of(1995, 3, 1), "서울", "회사원",
            178, "ENFP", "하루", "가끔", "비흡연", "무교"));

        profileService.updateProfile(userId, new UpdateProfileCommand(
            "FEMALE", LocalDate.of(1996, 6, 6), "부산", "학생",
            165, "INFJ", "이틀", "안함", "비흡연", "무교"));

        List<UserProfileEntity> rows = jpaRepository.findAll().stream()
            .filter(e -> e.toDomain().userId().equals(userId))
            .toList();
        assertThat(rows).hasSize(1);                 // upsert 계약: row 1개 유지 (중복 생성 없음)
        assertThat(rows.get(0).toDomain().nickname()).isEqualTo("이틀");
    }
}
