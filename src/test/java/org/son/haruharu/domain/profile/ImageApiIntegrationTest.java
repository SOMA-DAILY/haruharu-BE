package org.son.haruharu.domain.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.command.UpdateProfileCommand;
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
        Long userId = 2L;
        String base = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";
        String prefix = base + "users/" + userId + "/images/";
        given(s3StoragePort.publicBaseUrl()).willReturn(base);

        mockMvc.perform(put("/api/v1/users/me/images")
                .with(authentication(auth(userId)))
                .contentType(APPLICATION_JSON)
                .content("{\"image_urls\":[\"" + prefix + "a.jpg\",\"" + prefix + "b.jpg\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.image_urls[0]").value(prefix + "a.jpg"))
            .andExpect(jsonPath("$.data.image_urls[1]").value(prefix + "b.jpg"));
    }

    @Test
    @DisplayName("이미지가 0개면 IMAGE_400_MIN_LIMIT 에러를 반환한다 (최소 1장)")
    void givenNoImage_whenPutImages_thenReturnsMinLimitError() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/images")
                .with(authentication(auth(3L)))
                .contentType(APPLICATION_JSON)
                .content("{\"image_urls\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.error.code").value("IMAGE_400_MIN_LIMIT"));
    }

    @Test
    @DisplayName("마이페이지 조회 응답이 user_id·user_status·profile 중첩·images 객체배열(snake_case)로 직렬화되고 birthday는 노출되지 않는다")
    void givenProfile_whenGetMe_thenReturnsNestedSnakeCase() throws Exception {
        Long userId = 4L;
        profileService.updateProfile(userId, new UpdateProfileCommand(
            "서울", "회사원",
            178, "ENFP", "하루", "가끔", "비흡연"));

        mockMvc.perform(get("/api/v1/users/me").with(authentication(auth(userId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_id").exists())
            .andExpect(jsonPath("$.data.user_status").value("UNVERIFIED"))
            .andExpect(jsonPath("$.data.profile.nickname").value("하루"))
            // 미인증 유저는 나이(본인인증 값)가 없으므로 null
            .andExpect(jsonPath("$.data.profile.age").value(nullValue()))
            .andExpect(jsonPath("$.data.profile.drinking_status").value("가끔"))
            .andExpect(jsonPath("$.data.images").isArray())
            .andExpect(jsonPath("$.data.profile.birthday").doesNotExist());
    }
}
