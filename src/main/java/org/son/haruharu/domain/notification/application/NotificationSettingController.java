package org.son.haruharu.domain.notification.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.notification.application.dto.NotificationSettingRequest;
import org.son.haruharu.domain.notification.business.NotificationSettingService;
import org.son.haruharu.domain.notification.business.dto.NotificationSettingResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/notification-settings")
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    public NotificationSettingController(NotificationSettingService notificationSettingService) {
        this.notificationSettingService = notificationSettingService;
    }

    @GetMapping
    public ApiResponse<NotificationSettingResponse> getSetting(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationSettingService.getSetting(userId));
    }

    @PutMapping
    public ApiResponse<NotificationSettingResponse> updateSetting(
            Authentication authentication,
            @Valid @RequestBody NotificationSettingRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationSettingService.updateSetting(userId, request.toCommand()));
    }
}
