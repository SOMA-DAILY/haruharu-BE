package org.son.haruharu.domain.profile.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.profile.application.dto.ImageUpdateRequest;
import org.son.haruharu.domain.profile.application.dto.PresignedUrlRequest;
import org.son.haruharu.domain.profile.application.dto.ProfileUpdateRequest;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ImageUploadUrlResponse;
import org.son.haruharu.domain.profile.business.dto.ImagesResponse;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "프로필", description = "데이팅 프로필(nickname·mbti 등) 수정 및 프로필 이미지 업로드·교체")
@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "프로필 수정",
            description = "닉네임·지역·소속·키·mbti·음주/흡연 상태 등 데이팅 프로필을 수정한다. "
                    + "프로필이 없으면 404(USER_PROFILE_NOT_FOUND).")
    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(profileService.updateProfile(userId, request.toCommand()));
    }

    @Operation(summary = "이미지 업로드 URL 발급",
            description = "filename으로 S3 presigned URL을 발급한다. 응답의 presignedUrl로 직접 업로드하고, "
                    + "imageUrl을 이후 이미지 교체 API에 저장한다. 지원하지 않는 확장자면 400(IMAGE_400_INVALID_EXTENSION).")
    @PostMapping("/images/presigned-url")
    public ApiResponse<ImageUploadUrlResponse> createImageUploadUrl(
            @Valid @RequestBody PresignedUrlRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(profileService.createImageUploadUrl(userId, request.filename()));
    }

    @Operation(summary = "프로필 이미지 교체",
            description = "imageUrls로 프로필 이미지 목록을 통째로 교체한다(순서 유지). 각 URL은 본인이 발급받은 업로드 URL이어야 한다. "
                    + "본인 소유가 아니면 400(IMAGE_400_INVALID_URL), 최소 개수 미만이면 400(IMAGE_400_MIN_LIMIT), "
                    + "최대 개수 초과면 400(IMAGE_400_MAX_EXCEEDED).")
    @PutMapping("/images")
    public ApiResponse<ImagesResponse> updateImages(
            @Valid @RequestBody ImageUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(profileService.updateImages(userId, request.toCommand()));
    }
}
