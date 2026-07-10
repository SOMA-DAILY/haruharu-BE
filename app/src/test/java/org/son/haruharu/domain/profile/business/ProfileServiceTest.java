package org.son.haruharu.domain.profile.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.profile.business.command.UpdateImagesCommand;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.business.dto.ImageUploadUrlResponse;
import org.son.haruharu.domain.profile.business.dto.ImagesResponse;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.profile.business.exception.ImageInvalidExtensionException;
import org.son.haruharu.domain.profile.business.exception.ImageInvalidUrlException;
import org.son.haruharu.domain.profile.business.exception.ImageMinLimitException;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.implement.UserImageWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock UserProfileReader userProfileReader;
    @Mock UserProfileWriter userProfileWriter;
    @Mock UserImageReader userImageReader;
    @Mock UserImageWriter userImageWriter;
    @Mock S3StoragePort s3StoragePort;

    ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(
            userProfileReader, userProfileWriter,
            userImageReader, userImageWriter, s3StoragePort
        );
    }

    @Test
    @DisplayName("프로필 수정 시 저장 후 업데이트된 프로필과 이미지를 반환한다")
    void givenUpdateCommand_whenUpdateProfile_thenSavesAndReturnsProfileResponse() {
        Long userId = 1L;
        UpdateProfileCommand command = new UpdateProfileCommand(
            "서울", "직장인",
            175, "INFP", "새닉네임", DrinkingStatus.SOMETIMES, SmokingStatus.NONE
        );
        UserProfile existing = new UserProfile(1L, userId,
            "부산", "학생",
            175, "INFP", "기존닉", DrinkingStatus.NONE, SmokingStatus.NONE);
        UserProfile updated = command.applyTo(existing);

        given(userProfileReader.findByUserId(userId)).willReturn(Optional.of(existing));
        given(userProfileWriter.save(any())).willReturn(updated);
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of());

        ProfileResponse result = profileService.updateProfile(userId, command);

        assertThat(result.nickname()).isEqualTo("새닉네임");
        then(userProfileWriter).should().save(any());
    }

    @Test
    @DisplayName("프로필이 없는 신규 유저가 수정 요청 시 새 프로필을 생성해 저장한다")
    void givenNoProfile_whenUpdateProfile_thenCreatesAndSaves() {
        Long userId = 1L;
        UpdateProfileCommand command = new UpdateProfileCommand(
            "서울", "회사원",
            178, "ENFP", "하루", DrinkingStatus.SOMETIMES, SmokingStatus.NONE
        );

        given(userProfileReader.findByUserId(userId)).willReturn(Optional.empty());
        given(userProfileWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of());

        ProfileResponse result = profileService.updateProfile(userId, command);

        assertThat(result.nickname()).isEqualTo("하루");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileWriter).should().save(captor.capture());
        UserProfile saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.id()).isNull(); // 신규 row → id는 영속화 시점에 생성 위임
    }

    private static final String BASE_URL = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";

    /** 응답 presign을 원본 URL + "?sig" 패스스루로 스텁한다. */
    private void stubPresignImageGetPassthrough() {
        given(s3StoragePort.createPresignedImageGetUrl(anyString()))
            .willAnswer(invocation -> invocation.getArgument(0) + "?sig");
    }

    @Test
    @DisplayName("프로필 조회 시 이미지 URL을 presigned GET URL로 변환해 반환한다")
    void givenImages_whenGetProfile_thenReturnsPresignedImageUrls() {
        Long userId = 1L;
        String prefix = BASE_URL + "users/" + userId + "/images/";
        given(userProfileReader.getByUserId(userId)).willReturn(new UserProfile(1L, userId,
            "서울", "직장인", 170, "INFP", "하루", DrinkingStatus.NONE, SmokingStatus.NONE));
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of(
            new UserImage(1L, userId, prefix + "a.jpg", 0),
            new UserImage(2L, userId, prefix + "b.jpg", 1)
        ));
        stubPresignImageGetPassthrough();

        ProfileResponse result = profileService.getProfile(userId);

        assertThat(result.images())
            .extracting(ProfileResponse.ImageInfo::imageUrl)
            .containsExactly(prefix + "a.jpg?sig", prefix + "b.jpg?sig");
    }

    @Test
    @DisplayName("presigned URL(쿼리스트링 포함)로 이미지 업데이트 시 쿼리를 제거한 원본 URL로 저장하고 동일 객체는 S3에서 삭제하지 않는다")
    void givenPresignedUrls_whenUpdateImages_thenStoresCanonicalAndKeepsSameObjects() {
        Long userId = 1L;
        String prefix = BASE_URL + "users/" + userId + "/images/";
        // 클라이언트가 조회 응답의 presigned URL(a.jpg?...)을 그대로 되돌려 보내는 케이스
        UpdateImagesCommand command = new UpdateImagesCommand(List.of(
            prefix + "a.jpg?X-Amz-Signature=abc",
            prefix + "b.jpg"
        ));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE_URL);
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of(
            new UserImage(1L, userId, prefix + "a.jpg", 0)
        ));
        stubPresignImageGetPassthrough();

        profileService.updateImages(userId, command);

        // 기존 a.jpg는 canonical 비교로 orphan이 아님 → S3 삭제 대상 없음
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        then(s3StoragePort).should().deleteObjects(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).isEmpty();

        // 저장은 쿼리스트링이 제거된 canonical URL로
        ArgumentCaptor<UserImage> imageCaptor = ArgumentCaptor.forClass(UserImage.class);
        then(userImageWriter).should(times(2)).save(imageCaptor.capture());
        assertThat(imageCaptor.getAllValues())
            .extracting(UserImage::imageUrl)
            .containsExactly(prefix + "a.jpg", prefix + "b.jpg");
    }

    @Test
    @DisplayName("이미지 업데이트 시 기존 이미지를 전체 삭제하고 새 이미지를 순서대로 저장한 뒤 presigned URL 응답을 반환한다")
    void givenValidImages_whenUpdateImages_thenDeletesOldAndSavesNewAndReturnsResponse() {
        Long userId = 1L;
        String prefix = BASE_URL + "users/" + userId + "/images/";
        UpdateImagesCommand command = new UpdateImagesCommand(
            List.of(prefix + "a.jpg", prefix + "b.jpg", prefix + "c.jpg")
        );
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE_URL);
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of());
        stubPresignImageGetPassthrough();

        ImagesResponse response = profileService.updateImages(userId, command);

        then(userImageWriter).should().deleteAllByUserId(userId);
        then(userImageWriter).should(times(3)).save(any());
        assertThat(response.imageUrls())
            .containsExactly(prefix + "a.jpg?sig", prefix + "b.jpg?sig", prefix + "c.jpg?sig");
    }

    @Test
    @DisplayName("본인 prefix가 아닌 URL을 저장하려 하면 ImageInvalidUrlException이 발생하고 저장하지 않는다")
    void givenForeignUrl_whenUpdateImages_thenThrowsInvalidUrl() {
        Long userId = 1L;
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE_URL);
        UpdateImagesCommand command = new UpdateImagesCommand(List.of(
            BASE_URL + "users/" + userId + "/images/mine.jpg",
            BASE_URL + "users/" + 2L + "/images/stolen.jpg" // 타 유저 prefix
        ));

        assertThatThrownBy(() -> profileService.updateImages(userId, command))
            .isInstanceOf(ImageInvalidUrlException.class);

        then(userImageWriter).should(never()).deleteAllByUserId(any());
        then(userImageWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("이미지가 없으면(0개) ImageMinLimitException이 발생하고 저장하지 않는다")
    void givenNoImages_whenUpdateImages_thenThrowsMinLimit() {
        Long userId = 1L;
        UpdateImagesCommand command = new UpdateImagesCommand(List.of());

        assertThatThrownBy(() -> profileService.updateImages(userId, command))
            .isInstanceOf(ImageMinLimitException.class);

        then(userImageWriter).should(never()).deleteAllByUserId(any());
        then(userImageWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("대표 사진 1장만으로도 저장할 수 있다 (최소 1장)")
    void givenSingleImage_whenUpdateImages_thenSaves() {
        Long userId = 1L;
        String prefix = BASE_URL + "users/" + userId + "/images/";
        UpdateImagesCommand command = new UpdateImagesCommand(List.of(prefix + "main.jpg"));
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE_URL);
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of());
        stubPresignImageGetPassthrough();

        ImagesResponse response = profileService.updateImages(userId, command);

        then(userImageWriter).should().save(any());
        assertThat(response.imageUrls()).containsExactly(prefix + "main.jpg?sig");
    }

    @Test
    @DisplayName("이미지 전체 삭제 시 S3 객체를 먼저 삭제하고 DB 행을 삭제한다")
    void givenImages_whenDeleteAllImages_thenDeletesS3ObjectsAndDbRows() {
        Long userId = 1L;
        String prefix = BASE_URL + "users/" + userId + "/images/";
        given(s3StoragePort.publicBaseUrl()).willReturn(BASE_URL);
        given(userImageReader.getAllByUserId(userId)).willReturn(List.of(
            new UserImage(1L, userId, prefix + "a.jpg", 0),
            new UserImage(2L, userId, prefix + "b.jpg", 1)
        ));

        profileService.deleteAllImages(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        then(s3StoragePort).should().deleteObjects(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactly(
            "users/" + userId + "/images/a.jpg",
            "users/" + userId + "/images/b.jpg"
        );
        then(userImageWriter).should().deleteAllByUserId(userId);
    }

    @Test
    @DisplayName("유효한 파일명으로 presigned URL 발급 시 image_url과 presigned_url을 반환한다")
    void givenValidFilename_whenCreateUploadUrl_thenReturnsUrls() {
        Long userId = 1L;
        given(s3StoragePort.createPresignedPutUrl(anyString(), eq("image/jpeg")))
            .willReturn(new PresignedUpload("https://presigned-put", "https://bucket/object.jpg"));

        ImageUploadUrlResponse response = profileService.createImageUploadUrl(userId, "profile.jpg");

        assertThat(response.presignedUrl()).isEqualTo("https://presigned-put");
        assertThat(response.imageUrl()).isEqualTo("https://bucket/object.jpg");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        then(s3StoragePort).should().createPresignedPutUrl(keyCaptor.capture(), eq("image/jpeg"));
        assertThat(keyCaptor.getValue()).startsWith("users/" + userId + "/images/");
        assertThat(keyCaptor.getValue()).endsWith(".jpg");
    }

    @Test
    @DisplayName("허용되지 않는 확장자로 presigned URL 발급 시 ImageInvalidExtensionException이 발생한다")
    void givenInvalidExtension_whenCreateUploadUrl_thenThrows() {
        Long userId = 1L;

        assertThatThrownBy(() -> profileService.createImageUploadUrl(userId, "malware.exe"))
            .isInstanceOf(ImageInvalidExtensionException.class);

        then(s3StoragePort).should(never()).createPresignedPutUrl(anyString(), anyString());
    }
}
