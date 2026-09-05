package org.son.haruharu.domain.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
import org.son.haruharu.domain.profile.implement.UserImage;
import org.son.haruharu.domain.profile.storage.UserImageEntity;
import org.son.haruharu.domain.profile.storage.UserImageJpaRepository;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.storage.UserEntity;
import org.son.haruharu.domain.user.storage.UserJpaRepository;
import org.son.haruharu.infrastructure.s3.PresignedUpload;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ProfileService profileService;
    @Autowired UserJpaRepository userJpaRepository;
    @Autowired UserImageJpaRepository userImageJpaRepository;

    @MockitoBean S3StoragePort s3StoragePort;

    private static UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    @DisplayName("presigned URL 발급 응답이 snake_case(presigned_url, image_url)로 직렬화된다")
    void givenValidFilename_whenPostPresignedUrl_thenReturnsSnakeCaseUrls() throws Exception {
        given(s3StoragePort.createPresignedPutUrl(anyString(), anyString()))
            .willReturn(new PresignedUpload("https://presigned-put", "https://media/object.jpg"));

        mockMvc.perform(post("/api/v1/users/me/images/presigned-url")
                .with(authentication(auth(1L)))
                .contentType(APPLICATION_JSON)
                .content("{\"filename\":\"profile.jpg\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.data.presigned_url").value("https://presigned-put"))
            .andExpect(jsonPath("$.data.image_url").value("https://media/object.jpg"));
    }

    @Test
    @DisplayName("이미지 저장 응답이 정렬된 image_urls(snake_case 문자열 배열)로 통일되어 반환된다")
    void givenTwoImages_whenPutImages_thenReturnsImageUrls() throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        String base = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";
        String prefix = base + "users/" + userId + "/images/";
        given(s3StoragePort.publicBaseUrl()).willReturn(base);
        given(s3StoragePort.createPresignedImageGetUrl(anyString()))
            .willAnswer(invocation -> invocation.getArgument(0) + "?sig");

        mockMvc.perform(put("/api/v1/users/me/images")
                .with(authentication(auth(userId)))
                .contentType(APPLICATION_JSON)
                .content("{\"image_urls\":[\"" + prefix + "a.jpg\",\"" + prefix + "b.jpg\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.image_urls[0]").value(prefix + "a.jpg?sig"))
            .andExpect(jsonPath("$.data.image_urls[1]").value(prefix + "b.jpg?sig"));
    }

    @Test
    @DisplayName("빈 목록으로 교체하면 본인 사진만 전체 삭제되고 프로필 조회에도 반영되며 재삭제도 성공한다")
    void givenExistingImages_whenPutEmptyImages_thenDeletesOwnImagesAndCanRepeat() throws Exception {
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        Long otherUserId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        profileService.updateProfile(userId, new UpdateProfileCommand(
            null, null, null, null, "사진삭제테스트", null, null));
        String base = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";
        String firstKey = "users/" + userId + "/images/a.jpg";
        String secondKey = "users/" + userId + "/images/b.jpg";
        String otherUrl = base + "users/" + otherUserId + "/images/keep.jpg";
        given(s3StoragePort.publicBaseUrl()).willReturn(base);
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, userId, base + firstKey, 0)));
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, userId, base + secondKey, 1)));
        userImageJpaRepository.save(UserImageEntity.from(new UserImage(null, otherUserId, otherUrl, 0)));

        mockMvc.perform(put("/api/v1/users/me/images")
                .with(authentication(auth(userId)))
                .contentType(APPLICATION_JSON)
                .content("{\"image_urls\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.data.image_urls").isArray())
            .andExpect(jsonPath("$.data.image_urls").isEmpty());

        assertThat(userImageJpaRepository.findAllByUserIdOrderBySortedOrderAsc(userId)).isEmpty();
        assertThat(userImageJpaRepository.findAllByUserIdOrderBySortedOrderAsc(otherUserId))
            .extracting(image -> image.toDomain().imageUrl()).containsExactly(otherUrl);
        then(s3StoragePort).should().deleteObjects(List.of(firstKey, secondKey));
        mockMvc.perform(get("/api/v1/users/me").with(authentication(auth(userId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.images").isArray())
            .andExpect(jsonPath("$.data.images").isEmpty());

        mockMvc.perform(put("/api/v1/users/me/images")
                .with(authentication(auth(userId)))
                .contentType(APPLICATION_JSON)
                .content("{\"image_urls\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.image_urls").isArray())
            .andExpect(jsonPath("$.data.image_urls").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"image_urls\":null}"})
    @DisplayName("이미지 목록 누락과 null은 여전히 400으로 거절한다")
    void givenMissingOrNullImages_whenPutImages_thenReturnsBadRequest(String body) throws Exception {
        mockMvc.perform(put("/api/v1/users/me/images")
                .with(authentication(auth(3L)))
                .contentType(APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("마이페이지 조회 응답이 user_id·user_status·profile 중첩·images 객체배열(snake_case)로 직렬화되고 birthday는 노출되지 않는다")
    void givenProfile_whenGetMe_thenReturnsNestedSnakeCase() throws Exception {
        // /users/me는 users 행을 요구하므로 유저를 먼저 시드한다.
        Long userId = userJpaRepository.save(UserEntity.from(new User(null, UserStatus.ACTIVE, null))).toDomain().id();
        profileService.updateProfile(userId, new UpdateProfileCommand(
            "서울", "회사원",
            178, "ENFP", "하루", DrinkingStatus.SOMETIMES, SmokingStatus.NONE, null));

        mockMvc.perform(get("/api/v1/users/me").with(authentication(auth(userId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_id").exists())
            .andExpect(jsonPath("$.data.user_status").value("UNVERIFIED"))
            .andExpect(jsonPath("$.data.profile.nickname").value("하루"))
            // 미인증 유저는 나이(본인인증 값)가 없으므로 null
            .andExpect(jsonPath("$.data.profile.age").value(nullValue()))
            .andExpect(jsonPath("$.data.profile.drinking_status").value("SOMETIMES"))
            .andExpect(jsonPath("$.data.images").isArray())
            .andExpect(jsonPath("$.data.profile.birthday").doesNotExist());
    }
}
