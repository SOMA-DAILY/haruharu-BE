package org.son.haruharu.domain.notification.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.notification.application.dto.DeviceTokenRequest;
import org.son.haruharu.domain.notification.business.DeviceTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping
    public ApiResponse<Void> register(
            @Valid @RequestBody DeviceTokenRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        deviceTokenService.register(userId, request.token());
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> unregister(
            @Valid @RequestBody DeviceTokenRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        deviceTokenService.unregister(userId, request.token());
        return ApiResponse.ok(null);
    }
}
