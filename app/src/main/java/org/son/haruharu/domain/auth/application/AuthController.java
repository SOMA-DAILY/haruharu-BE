package org.son.haruharu.domain.auth.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.auth.application.dto.LogoutRequest;
import org.son.haruharu.domain.auth.application.dto.SocialAuthRequest;
import org.son.haruharu.domain.auth.application.dto.TokenRefreshRequest;
import org.son.haruharu.domain.auth.business.AuthService;
import org.son.haruharu.domain.auth.business.dto.AuthTokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증/토큰", description = "소셜 로그인·회원가입, JWT 액세스/리프레시 토큰 발급·갱신·로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@SecurityRequirements // 비인증 엔드포인트 — Swagger 전역 bearerAuth 해제(자물쇠 미표시)
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "소셜 로그인/회원가입",
            description = "provider_type(KAKAO/APPLE)와 id_token으로 인증한다. 신규면 가입, 기존이면 로그인 처리 후 "
                    + "access_token·refresh_token·user_status를 반환한다. 유효하지 않은 idToken이면 401(AUTH_INVALID_ID_TOKEN), "
                    + "비활성 계정이면 403(AUTH_INACTIVE_MEMBER). 탈퇴 후 재가입은 즉시 허용되며(#245), "
                    + "복귀 유저에게는 첫 가입 보너스 구름이 재지급되지 않는다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@RequestBody @Valid SocialAuthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.authenticate(request.toCommand())));
    }

    @Operation(summary = "액세스 토큰 갱신",
            description = "refresh_token으로 새 access_token·refresh_token을 재발급한다(리프레시 재사용 탐지). "
                    + "유효하지 않거나 만료된 토큰이면 401(AUTH_INVALID_REFRESH_TOKEN), 저장된 토큰이 없으면 404(REFRESH_TOKEN_NOT_FOUND).")
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(@RequestBody @Valid TokenRefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.toCommand())));
    }

    @Operation(summary = "로그아웃",
            description = "전달한 refresh_token을 무효화한다. 이후 해당 토큰으로는 갱신할 수 없다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
