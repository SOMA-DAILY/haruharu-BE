package org.son.haruharu.domain.user.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.user.business.dto.OnboardingStatusResponse;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserProfileResponse> getMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(userService.getMyProfile(userId));
    }

    @PostMapping("/onboarding/complete")
    public ApiResponse<OnboardingStatusResponse> completeOnboarding(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(userService.completeOnboarding(userId));
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.withdraw(userId);
        return ApiResponse.ok(null);
    }
}
