package org.son.haruharu.domain.notification.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.notification.business.NotificationHistoryService;
import org.son.haruharu.domain.notification.business.dto.NotificationsResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림함", description = "유저 알림함 목록 조회(커서 페이지네이션)와 전체 읽음 처리 API")
@RestController
@RequestMapping("/api/v1/users/me/notifications")
public class NotificationHistoryController {

    private final NotificationHistoryService notificationHistoryService;

    public NotificationHistoryController(NotificationHistoryService notificationHistoryService) {
        this.notificationHistoryService = notificationHistoryService;
    }

    @Operation(summary = "알림함 목록 조회",
            description = "로그인 유저의 알림을 최신순 커서 페이지네이션으로 조회한다. 응답은 notifications 배열과 함께 "
                    + "next_cursor(다음 페이지 커서, 없으면 null), unread_count(안 읽은 알림 수)를 포함한다. "
                    + "각 알림의 payload는 type별 딥링크 키를 담는다 — roomId: ROOM_MATCHED·ROOM_STARTED·FINAL_SELECTION_OPEN·"
                    + "MUTUAL_MATCH·SELECTED_BY_SOMEONE / videoPostId: REACTION_RECEIVED / "
                    + "chatRoomId: CHAT / 빈 객체: COIN_REFUNDED. 값은 전부 문자열.")
    @GetMapping
    public ApiResponse<NotificationsResponse> getNotifications(
            @Parameter(description = "이전 페이지 마지막 알림 id. 첫 페이지는 생략", example = "42")
            @RequestParam(required = false) Long cursor,
            @Parameter(description = "페이지 크기(기본 20)", example = "20")
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationHistoryService.getNotifications(userId, cursor, size));
    }

    @Operation(summary = "알림 전체 읽음 처리",
            description = "로그인 유저의 안 읽은 알림을 모두 읽음 상태로 변경한다. 이후 unread_count는 0이 된다.")
    @PatchMapping("/read")
    public ApiResponse<Void> markAllRead(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        notificationHistoryService.markAllRead(userId);
        return ApiResponse.ok(null);
    }
}
