package org.son.haruharu.domain.room.application;

import jakarta.validation.Valid;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.room.application.dto.FinalSelectionRequest;
import org.son.haruharu.domain.room.business.FinalSelectionService;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.MemberProfileService;
import org.son.haruharu.domain.room.business.RoomFeedService;
import org.son.haruharu.domain.room.business.RoomMembersService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.RoomTodayService;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
public class RoomController {

    private final RoomService roomService;
    private final RoomTodayService roomTodayService;
    private final RoomMembersService roomMembersService;
    private final MemberProfileService memberProfileService;
    private final FinalSelectionService finalSelectionService;
    private final MatchResultService matchResultService;
    private final RoomFeedService roomFeedService;

    public RoomController(RoomService roomService, RoomTodayService roomTodayService,
                          RoomMembersService roomMembersService, MemberProfileService memberProfileService,
                          FinalSelectionService finalSelectionService,
                          MatchResultService matchResultService, RoomFeedService roomFeedService) {
        this.roomService = roomService;
        this.roomTodayService = roomTodayService;
        this.roomMembersService = roomMembersService;
        this.memberProfileService = memberProfileService;
        this.finalSelectionService = finalSelectionService;
        this.matchResultService = matchResultService;
        this.roomFeedService = roomFeedService;
    }

    @GetMapping("/me")
    public ApiResponse<RoomResponse> getMyRoom(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomService.getMyRoom(userId));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(@PathVariable Long roomId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomService.getRoom(roomId, userId));
    }

    @GetMapping("/{roomId}/today")
    public ApiResponse<TodayResponse> getToday(@PathVariable Long roomId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomTodayService.getToday(roomId, userId));
    }

    @GetMapping("/{roomId}/members")
    public ApiResponse<MembersResponse> getMembers(@PathVariable Long roomId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomMembersService.getMembers(roomId, userId));
    }

    @GetMapping("/{roomId}/members/{memberId}/profile")
    public ApiResponse<MemberProfileResponse> getMemberProfile(@PathVariable Long roomId, @PathVariable Long memberId,
                                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(memberProfileService.getMemberProfile(roomId, memberId, userId));
    }

    @PostMapping("/{roomId}/final-selection")
    public ApiResponse<FinalSelectionResponse> selectFinal(@PathVariable Long roomId,
                                                           @Valid @RequestBody FinalSelectionRequest request,
                                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(finalSelectionService.select(roomId, request.selectedMemberId(), userId));
    }

    @GetMapping("/{roomId}/match-result")
    public ApiResponse<MatchResultResponse> getMatchResult(@PathVariable Long roomId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(matchResultService.getMatchResult(roomId, userId));
    }

    @GetMapping("/{roomId}/feed")
    public ApiResponse<FeedResponse> getFeed(@PathVariable Long roomId, @RequestParam int day,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomFeedService.getFeed(roomId, userId, day));
    }
}
