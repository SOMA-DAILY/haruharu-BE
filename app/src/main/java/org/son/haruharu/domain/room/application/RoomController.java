package org.son.haruharu.domain.room.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "소개팅방", description = "3일 소개팅방 조회·오늘 화면·멤버 프로필·영상 피드·Day3 최종선택·매칭 결과")
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

    @Operation(summary = "내 소개팅방 조회",
            description = "로그인 유저가 참여 중인(종료되지 않은) 소개팅방을 반환한다. room_id·status·current_day·member_count 등을 담는다. "
                    + "참여 중인 방이 없으면 404(ROOM_404_NOT_FOUND).")
    @GetMapping("/me")
    public ApiResponse<RoomResponse> getMyRoom(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomService.getMyRoom(userId));
    }

    @Operation(summary = "소개팅방 단건 조회",
            description = "room_id로 소개팅방을 조회한다. 요청자가 해당 방 참여자여야 하며 아니면 403(ROOM_403_NOT_MEMBER), "
                    + "방이 없으면 404(ROOM_404_NOT_FOUND).")
    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> getRoom(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomService.getRoom(roomId, userId));
    }

    @Operation(summary = "오늘 화면 조회",
            description = "현재 Day의 업로드 슬롯(시간창·현재 열림 여부)과 내 업로드 진행(uploaded_count)·완주 보상(reward_status)·"
                    + "헤더 타이머(timer_target_at, timer_type)를 반환한다. 참여자가 아니면 403(ROOM_403_NOT_MEMBER), "
                    + "방이 없으면 404(ROOM_404_NOT_FOUND).")
    @GetMapping("/{roomId}/today")
    public ApiResponse<TodayResponse> getToday(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomTodayService.getToday(roomId, userId));
    }

    @Operation(summary = "방 멤버 목록 조회",
            description = "방 참여자 목록을 반환한다. 항상 공개되는 기본 정보(nickname·gender·location·age)만 담으며, "
                    + "Day 게이트 항목은 멤버 단건 프로필 조회에서 다룬다. 참여자가 아니면 403(ROOM_403_NOT_MEMBER).")
    @GetMapping("/{roomId}/members")
    public ApiResponse<MembersResponse> getMembers(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                   Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomMembersService.getMembers(roomId, userId));
    }

    @Operation(summary = "방 멤버 단건 프로필 조회",
            description = "같은 방 멤버(room_member_id)의 프로필을 반환한다. 항목별 공개는 current_day 기반 자동 공개이며, "
                    + "본인이거나 상호매칭이 성사된 상대는 Day와 무관하게 전 항목 공개된다. 참여자가 아니면 403(ROOM_403_NOT_MEMBER), "
                    + "대상 멤버가 없거나 다른 방 소속이면 404(ROOM_MEMBER_404_NOT_FOUND), 방이 없으면 404(ROOM_404_NOT_FOUND).")
    @GetMapping("/{roomId}/members/{memberId}/profile")
    public ApiResponse<MemberProfileResponse> getMemberProfile(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                               @Parameter(description = "조회할 방 멤버 id(room_member_id)", example = "10") @PathVariable Long memberId,
                                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(memberProfileService.getMemberProfile(roomId, memberId, userId));
    }

    @Operation(summary = "Day3 최종선택",
            description = "마지막 날(Day3) 마지막 슬롯 종료 후 최종선택을 확정한다. 멤버당 1회만 가능(변경 불가)이며, "
                    + "상대도 나를 선택했으면 즉시 상호매칭이 성사된다. 요청 body의 selected_member_id가 null이면 기권 처리된다. "
                    + "최종선택 시간 전이면 409(ROOM_409_NOT_FINAL_DAY), 자기 자신 선택은 400(ROOM_400_CANNOT_SELECT_SELF), "
                    + "이미 선택했으면 409(ROOM_409_ALREADY_SELECTED), 대상이 같은 방 멤버가 아니면 404(ROOM_MEMBER_404_NOT_FOUND), "
                    + "참여자가 아니면 403(ROOM_403_NOT_MEMBER).")
    @PostMapping("/{roomId}/final-selection")
    public ApiResponse<FinalSelectionResponse> selectFinal(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                           @Valid @RequestBody FinalSelectionRequest request,
                                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(finalSelectionService.select(roomId, request.selectedMemberId(), userId));
    }

    @Operation(summary = "내 매칭 결과 조회",
            description = "상호선택으로 성사된 내 매칭 상대의 방 멤버 id(partner_member_id)와 성사 시각(matched_at)을 반환한다. "
                    + "참여자가 아니면 403(ROOM_403_NOT_MEMBER), 성사된 매칭이 없으면 404(ROOM_MATCH_404_NOT_FOUND).")
    @GetMapping("/{roomId}/match-result")
    public ApiResponse<MatchResultResponse> getMatchResult(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(matchResultService.getMatchResult(roomId, userId));
    }

    @Operation(summary = "Day별 영상 피드 조회",
            description = "지정한 day의 영상 피드를 반환한다. 잠금은 슬롯(시간대) 단위로, 내가 업로드한 슬롯의 타 멤버 영상만 열람할 수 있다(참여 유도). "
                    + "각 영상은 반응 수(reaction_counts)와 내 반응(my_reaction)을 함께 담는다. 참여자가 아니면 403(ROOM_403_NOT_MEMBER).")
    @GetMapping("/{roomId}/feed")
    public ApiResponse<FeedResponse> getFeed(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                             @Parameter(description = "조회할 Day(1~3)", example = "1") @RequestParam int day,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomFeedService.getFeed(roomId, userId, day));
    }
}
