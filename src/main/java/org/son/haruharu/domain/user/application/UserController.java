package org.son.haruharu.domain.user.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.user.application.dto.IdentityVerifyRequest;
import org.son.haruharu.domain.user.application.dto.ImageUpdateRequest;
import org.son.haruharu.domain.user.application.dto.ProfileUpdateRequest;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserProfileResponse> getMyProfile(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(userService.getMyProfile(userId));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(userService.updateProfile(userId, request.toCommand()));
    }

    @PutMapping("/images")
    public ApiResponse<Void> updateImages(
            @Valid @RequestBody ImageUpdateRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        userService.updateImages(userId, request.toCommand());
        return ApiResponse.ok(null);
    }

    @PostMapping("/identity")
    public ApiResponse<Void> verifyIdentity(
            @Valid @RequestBody IdentityVerifyRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        userService.verifyIdentity(userId, request.toCommand());
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        userService.withdraw(userId);
        return ApiResponse.ok(null);
    }
}
