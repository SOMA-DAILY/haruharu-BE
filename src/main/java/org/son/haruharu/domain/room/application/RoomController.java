package org.son.haruharu.domain.room.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.room.business.RoomFeedService;
import org.son.haruharu.domain.room.business.RoomMembersService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.RoomTodayService;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomTodayService roomTodayService;
    private final RoomMembersService roomMembersService;
    private final RoomFeedService roomFeedService;

    public RoomController(RoomService roomService, RoomTodayService roomTodayService,
                          RoomMembersService roomMembersService, RoomFeedService roomFeedService) {
        this.roomService = roomService;
        this.roomTodayService = roomTodayService;
        this.roomMembersService = roomMembersService;
        this.roomFeedService = roomFeedService;
    }

    @GetMapping("/me")
    public ApiResponse<RoomResponse> getMyRoom(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(roomService.getMyRoom(userId));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(@PathVariable UUID roomId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(roomService.getRoom(roomId, userId));
    }

    @GetMapping("/{roomId}/today")
    public ApiResponse<TodayResponse> getToday(@PathVariable UUID roomId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(roomTodayService.getToday(roomId, userId));
    }

    @GetMapping("/{roomId}/members")
    public ApiResponse<MembersResponse> getMembers(@PathVariable UUID roomId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(roomMembersService.getMembers(roomId, userId));
    }

    @GetMapping("/{roomId}/feed")
    public ApiResponse<FeedResponse> getFeed(@PathVariable UUID roomId, @RequestParam int day,
                                             Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(roomFeedService.getFeed(roomId, userId, day));
    }
}
