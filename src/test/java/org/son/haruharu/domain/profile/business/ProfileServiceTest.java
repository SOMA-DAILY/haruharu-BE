package org.son.haruharu.domain.profile.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.business.command.UpdateImagesCommand;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserImageWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock UserProfileReader userProfileReader;
    @Mock UserProfileWriter userProfileWriter;
    @Mock UserImageReader userImageReader;
    @Mock UserImageWriter userImageWriter;

    ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(
            userProfileReader, userProfileWriter,
            userImageReader, userImageWriter
        );
    }

    @Test
    @DisplayName("프로필 수정 시 저장 후 업데이트된 프로필과 이미지를 반환한다")
    void givenUpdateCommand_whenUpdateProfile_thenSavesAndReturnsProfileResponse() {
        UUID userId = UUID.randomUUID();
        UpdateProfileCommand command = new UpdateProfileCommand(
            "MALE", LocalDate.of(1998, 5, 15), "서울", "직장인",
            175, "INFP", "새닉네임", "가끔", "비흡연", "무교", "잔잔한"
        );
        UserProfile existing = new UserProfile(UUID.randomUUID(), userId,
            "MALE", LocalDate.of(1998, 5, 15), "부산", "학생",
            175, "INFP", "기존닉", "안함", "비흡연", "무교", "활발한");
        UserProfile updated = command.applyTo(existing);

        given(userProfileReader.getByUserId(userId)).willReturn(existing);
        given(userProfileWriter.save(any())).willReturn(updated);
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of());

        ProfileResponse result = profileService.updateProfile(userId, command);

        assertThat(result.nickname()).isEqualTo("새닉네임");
        then(userProfileWriter).should().save(any());
    }

    @Test
    @DisplayName("이미지 업데이트 시 기존 이미지를 전체 삭제하고 새 이미지를 순서대로 저장한다")
    void givenUpdateImagesCommand_whenUpdateImages_thenDeletesOldAndSavesNew() {
        UUID userId = UUID.randomUUID();
        UpdateImagesCommand command = new UpdateImagesCommand(
            List.of("https://new1.png", "https://new2.png", "https://new3.png")
        );

        profileService.updateImages(userId, command);

        then(userImageWriter).should().deleteAllByUserId(userId);
        then(userImageWriter).should(times(3)).save(any());
    }
}
