package org.son.haruharu.domain.room.application;

import org.son.haruharu.common.ApiResponse;
import jakarta.validation.Valid;
import org.son.haruharu.domain.room.application.dto.FinalSelectionRequest;
import org.son.haruharu.domain.room.business.FinalSelectionService;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.MemberProfileService;
import org.son.haruharu.domain.room.business.ProfileFieldUnlockService;
import org.son.haruharu.domain.room.business.RoomFeedService;
import org.son.haruharu.domain.room.business.RoomMembersService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.RoomTodayService;
import org.son.haruharu.domain.room.business.ValueAnswerUnlockService;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.ProfileFieldUnlockResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.son.haruharu.domain.room.business.dto.UnlockResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final MemberProfileService memberProfileService;
    private final ValueAnswerUnlockService valueAnswerUnlockService;
    private final ProfileFieldUnlockService profileFieldUnlockService;
    private final FinalSelectionService finalSelectionService;
    private final MatchResultService matchResultService;
    private final RoomFeedService roomFeedService;

    public RoomController(RoomService roomService, RoomTodayService roomTodayService,
                          RoomMembersService roomMembersService, MemberProfileService memberProfileService,
                          ValueAnswerUnlockService valueAnswerUnlockService,
                          ProfileFieldUnlockService profileFieldUnlockService,
                          FinalSelectionService finalSelectionService,
                          MatchResultService matchResultService, RoomFeedService roomFeedService) {
        this.roomService = roomService;
        this.roomTodayService = roomTodayService;
        this.roomMembersService = roomMembersService;
        this.memberProfileService = memberProfileService;
        this.valueAnswerUnlockService = valueAnswerUnlockService;
        this.profileFieldUnlockService = profileFieldUnlockService;
        this.finalSelectionService = finalSelectionService;
        this.matchResultService = matchResultService;
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

    @GetMapping("/{roomId}/members/{memberId}/profile")
    public ApiResponse<MemberProfileResponse> getMemberProfile(@PathVariable UUID roomId, @PathVariable UUID memberId,
                                                               Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(memberProfileService.getMemberProfile(roomId, memberId, userId));
    }

    @PostMapping("/{roomId}/members/{memberId}/value-answers/{answerId}/unlock")
    public ApiResponse<UnlockResponse> unlockValueAnswer(@PathVariable UUID roomId, @PathVariable UUID memberId,
                                                         @PathVariable UUID answerId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(valueAnswerUnlockService.unlock(roomId, memberId, answerId, userId));
    }

    @PostMapping("/{roomId}/members/{memberId}/profile-fields/{fieldKey}/unlock")
    public ApiResponse<ProfileFieldUnlockResponse> unlockProfileField(@PathVariable UUID roomId,
                                                                      @PathVariable UUID memberId,
                                                                      @PathVariable String fieldKey,
                                                                      Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(profileFieldUnlockService.unlock(roomId, memberId, fieldKey, userId));
    }

    @PostMapping("/{roomId}/final-selection")
    public ApiResponse<FinalSelectionResponse> selectFinal(@PathVariable UUID roomId,
                                                           @Valid @RequestBody FinalSelectionRequest request,
                                                           Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(finalSelectionService.select(roomId, request.selectedMemberId(), userId));
    }

    @GetMapping("/{roomId}/match-result")
    public ApiResponse<MatchResultResponse> getMatchResult(@PathVariable UUID roomId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(matchResultService.getMatchResult(roomId, userId));
    }

    @GetMapping("/{roomId}/feed")
    public ApiResponse<FeedResponse> getFeed(@PathVariable UUID roomId, @RequestParam int day,
                                             Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(roomFeedService.getFeed(roomId, userId, day));
    }
}
