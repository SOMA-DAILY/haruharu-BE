package org.son.haruharu.domain.user.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "유저", description = "내 계정 조회, 온보딩 완료 처리, 회원 탈퇴")
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "내 정보 조회",
            description = "로그인한 사용자의 프로필(nickname·gender·age 등)과 이미지 목록, user_status를 반환한다. "
                    + "gender·age는 본인인증 정보에서 수급한다. 사용자가 없으면 404(USER_NOT_FOUND).")
    @GetMapping
    public ApiResponse<UserProfileResponse> getMyProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(userService.getMyProfile(userId));
    }

    @Operation(summary = "온보딩 완료 처리",
            description = "공백이 아닌 닉네임이 등록된 경우 온보딩을 완료 처리하고 갱신된 user_status를 반환한다. "
                    + "닉네임이 미등록이면 400(ONBOARDING_INCOMPLETE), 사용자가 없으면 404(USER_NOT_FOUND).")
    @PostMapping("/onboarding/complete")
    public ApiResponse<OnboardingStatusResponse> completeOnboarding(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(userService.completeOnboarding(userId));
    }

    @Operation(summary = "회원 탈퇴",
            description = "로그인한 사용자의 계정을 탈퇴 처리한다. 탈퇴 후 30일간 재가입이 제한된다. 사용자가 없으면 404(USER_NOT_FOUND).")
    @DeleteMapping
    public ApiResponse<Void> withdraw(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.withdraw(userId);
        return ApiResponse.ok(null);
    }
}
