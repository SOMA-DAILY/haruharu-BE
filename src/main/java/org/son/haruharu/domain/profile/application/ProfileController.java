package org.son.haruharu.domain.profile.application;

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

@RestController
@RequestMapping("/api/v1/users/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(profileService.updateProfile(userId, request.toCommand()));
    }

    @PostMapping("/images/presigned-url")
    public ApiResponse<ImageUploadUrlResponse> createImageUploadUrl(
            @Valid @RequestBody PresignedUrlRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(profileService.createImageUploadUrl(userId, request.filename()));
    }

    @PutMapping("/images")
    public ApiResponse<ImagesResponse> updateImages(
            @Valid @RequestBody ImageUpdateRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(profileService.updateImages(userId, request.toCommand()));
    }
}
