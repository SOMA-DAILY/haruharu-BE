package org.son.haruharu.domain.notification.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "알림 설정", description = "유저 알림 수신 설정(활동/마케팅) 조회 및 변경 API")
@RestController
@RequestMapping("/api/v1/users/me/notification-settings")
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    public NotificationSettingController(NotificationSettingService notificationSettingService) {
        this.notificationSettingService = notificationSettingService;
    }

    @Operation(summary = "알림 설정 조회",
            description = "로그인 유저의 알림 수신 설정을 조회한다. 설정이 없으면 기본값을 반환한다. "
                    + "응답 필드는 activity_enabled(활동 알림), marketing_enabled(마케팅 알림).")
    @GetMapping
    public ApiResponse<NotificationSettingResponse> getSetting(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationSettingService.getSetting(userId));
    }

    @Operation(summary = "알림 설정 변경",
            description = "로그인 유저의 알림 수신 설정을 갱신한다. body 필드 activity_enabled, marketing_enabled는 모두 필수(true/false). "
                    + "갱신된 설정을 반환한다.")
    @PutMapping
    public ApiResponse<NotificationSettingResponse> updateSetting(
            Authentication authentication,
            @Valid @RequestBody NotificationSettingRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationSettingService.updateSetting(userId, request.toCommand()));
    }
}
