package org.son.haruharu.domain.identity.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.identity.application.dto.IdentityVerifyRequest;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "본인인증", description = "PASS 실명인증으로 CI/DI·이름·생년월일을 확인해 계정에 연결")
@RestController
@RequestMapping("/api/v1/users/me")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @Operation(summary = "본인인증 등록",
            description = "PASS 인증 결과 식별자(pass_token)로 실명인증 정보를 조회해 계정에 연결한다. "
                    + "이미 다른 계정에 연결된 정보면 409(IDENTITY_ALREADY_LINKED), 이미 인증을 마친 사용자면 409(USER_ALREADY_VERIFIED), "
                    + "탈퇴 후 30일 이내 재가입이면 403(IDENTITY_REJOIN_BLOCKED).")
    @PostMapping("/identity")
    public ApiResponse<Void> verifyIdentity(
            @Valid @RequestBody IdentityVerifyRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        identityService.verifyIdentity(userId, request.toCommand());
        return ApiResponse.ok(null);
    }
}
