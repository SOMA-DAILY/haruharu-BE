package org.son.haruharu.domain.notification.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "디바이스 토큰", description = "푸시 알림 발송을 위한 FCM 디바이스 토큰 등록/해제 API")
@RestController
@RequestMapping("/api/v1/users/me/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @Operation(summary = "디바이스 토큰 등록",
            description = "로그인 유저의 device_token을 등록(upsert)한다. body의 token은 최대 512자. 이미 있으면 갱신된다.")
    @PostMapping
    public ApiResponse<Void> register(
            @Valid @RequestBody DeviceTokenRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        deviceTokenService.register(userId, request.token());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "디바이스 토큰 해제",
            description = "로그인 유저의 해당 device_token을 삭제한다(로그아웃/기기 해제 시). body의 token으로 대상을 식별한다.")
    @DeleteMapping
    public ApiResponse<Void> unregister(
            @Valid @RequestBody DeviceTokenRequest request, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        deviceTokenService.unregister(userId, request.token());
        return ApiResponse.ok(null);
    }
}
