package org.son.haruharu.domain.identity.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.identity.application.dto.IdentityVerifyRequest;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/identity")
    public ApiResponse<Void> verifyIdentity(
            @Valid @RequestBody IdentityVerifyRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        identityService.verifyIdentity(userId, request.toCommand());
        return ApiResponse.ok(null);
    }
}
