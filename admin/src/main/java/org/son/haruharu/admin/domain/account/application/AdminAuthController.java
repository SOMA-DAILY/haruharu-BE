package org.son.haruharu.admin.domain.account.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.account.application.dto.AdminLoginRequest;
import org.son.haruharu.admin.domain.account.business.AdminAuthService;
import org.son.haruharu.admin.domain.account.business.dto.AdminMeResponse;
import org.son.haruharu.admin.domain.account.business.dto.AdminTokenResponse;
import org.son.haruharu.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Auth", description = "어드민 staff 인증(로그인/로그아웃/내 정보)")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "관리자 로그인", description = "email/password 검증 후 role claim이 담긴 어드민 토큰을 발급한다.")
    @PostMapping("/auth/login")
    public ApiResponse<AdminTokenResponse> login(@RequestBody @Valid AdminLoginRequest request) {
        return ApiResponse.ok(adminAuthService.login(request.toCommand()));
    }

    @Operation(summary = "관리자 로그아웃")
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long adminId) {
        adminAuthService.logout(adminId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "로그인한 관리자 정보 조회")
    @GetMapping("/me")
    public ApiResponse<AdminMeResponse> me(@AuthenticationPrincipal Long adminId) {
        return ApiResponse.ok(adminAuthService.getMe(adminId));
    }
}
