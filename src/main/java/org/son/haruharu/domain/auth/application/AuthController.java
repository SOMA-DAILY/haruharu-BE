package org.son.haruharu.domain.auth.application;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@SecurityRequirements // 비인증 엔드포인트 — Swagger 전역 bearerAuth 해제(자물쇠 미표시)
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@RequestBody @Valid SocialAuthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.authenticate(request.toCommand())));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(@RequestBody @Valid TokenRefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.toCommand())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request.toCommand());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
