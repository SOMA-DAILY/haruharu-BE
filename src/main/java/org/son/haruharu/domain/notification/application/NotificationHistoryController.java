package org.son.haruharu.domain.notification.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.notification.business.NotificationHistoryService;
import org.son.haruharu.domain.notification.business.dto.NotificationsResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
public class NotificationHistoryController {

    private final NotificationHistoryService notificationHistoryService;

    public NotificationHistoryController(NotificationHistoryService notificationHistoryService) {
        this.notificationHistoryService = notificationHistoryService;
    }

    @GetMapping
    public ApiResponse<NotificationsResponse> getNotifications(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationHistoryService.getNotifications(userId, cursor, size));
    }

    @PatchMapping("/read")
    public ApiResponse<Void> markAllRead(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        notificationHistoryService.markAllRead(userId);
        return ApiResponse.ok(null);
    }
}
