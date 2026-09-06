package org.son.haruharu.domain.room.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.room.application.dto.FinalSelectionRequest;
import org.son.haruharu.domain.room.application.dto.JoinRoomRequest;
import org.son.haruharu.domain.room.business.FinalSelectionService;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.MemberProfileService;
import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.mission.business.RoomMissionService;
import org.son.haruharu.domain.mission.business.MissionMemberStateService;
import org.son.haruharu.domain.mission.business.dto.RoomMissionResponse;
import org.son.haruharu.domain.room.business.RoomFeedService;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomListService;
import org.son.haruharu.domain.room.business.RoomMembersService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.RoomTodayService;
import org.son.haruharu.domain.room.business.dto.FeedResponse;
import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.dto.MatchResultResponse;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.MembersResponse;
import org.son.haruharu.domain.room.business.dto.MyFinalSelectionResponse;
import org.son.haruharu.domain.room.business.dto.RoomResponse;
import org.son.haruharu.domain.room.business.dto.RoomsResponse;
import org.son.haruharu.domain.room.business.dto.TodayResponse;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final RoomListService roomListService;
    private final RoomInviteService roomInviteService;
    private final RoomTodayService roomTodayService;
    private final RoomMembersService roomMembersService;
    private final MemberProfileService memberProfileService;
    private final FinalSelectionService finalSelectionService;
    private final MatchResultService matchResultService;
    private final RoomFeedService roomFeedService;
    private final RoomMissionService roomMissionService;
    private final MissionMemberStateService missionMemberStateService;

    public RoomController(RoomService roomService, RoomListService roomListService, RoomInviteService roomInviteService,
                          RoomTodayService roomTodayService,
                          RoomMembersService roomMembersService, MemberProfileService memberProfileService,
                          FinalSelectionService finalSelectionService,
                          MatchResultService matchResultService, RoomFeedService roomFeedService,
                          RoomMissionService roomMissionService,
                          MissionMemberStateService missionMemberStateService) {
        this.roomService = roomService;
        this.roomListService = roomListService;
        this.roomInviteService = roomInviteService;
        this.roomTodayService = roomTodayService;
        this.roomMembersService = roomMembersService;
        this.memberProfileService = memberProfileService;
        this.finalSelectionService = finalSelectionService;
        this.matchResultService = matchResultService;
        this.roomFeedService = roomFeedService;
        this.roomMissionService = roomMissionService;
        this.missionMemberStateService = missionMemberStateService;
    }

    @Operation(summary = "참여 중인 소개팅방 목록 조회",
            description = "로그인 유저의 PENDING·ACTIVE·SELECTING 방을 서버 기준 하나의 now로 반환한다. "
                    + "방은 ACTIVE·SELECTING·PENDING, 다음 timer_target_at, room_id 순으로 정렬되며 "
                    + "profile_image_urls는 고정 상한이나 패딩 없이 본인을 먼저 두고 실제 방 참여자와 같은 수의 슬롯을 반환한다. "
                    + "사진이 없는 참여자의 null 위치를 보존하고, 방 참여자가 없으면 빈 배열을 반환한다. "
                    + "참여 방 자체가 없을 때도 빈 rooms 배열을 200으로 반환한다.")
    @GetMapping
    public ApiResponse<RoomsResponse> getRooms(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomListService.getRooms(userId));
    }

    @Operation(summary = "초대코드로 방 입장",
            description = "운영진이 발급한 초대코드로 소개팅방에 입장한다. room_member가 생성되고 입장한 room_id를 반환한다. "
                    + "코드가 없으면 404(INVITE_CODE_NOT_FOUND), 만료·비활성·정원 초과이거나 종료·취소된 방이면 400(INVITE_CODE_NOT_USABLE), "
                    + "같은 방 재입장은 기존 room_id를 멱등 반환한다.")
    @PostMapping("/join")
    public ApiResponse<JoinRoomResponse> join(@Valid @RequestBody JoinRoomRequest request,
                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomInviteService.join(userId, request.code()));
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
            description = "현재 노출 중인 슬롯 창(시간창·현재 촬영 슬롯 open_now·내 업로드 여부 uploaded)과 내 업로드 진행(uploaded_count)·"
                    + "헤더 타이머(timer_target_at, timer_type)를 반환한다. slots는 공개 가능한 영상이 있는 시간대와 "
                    + "ACTIVE 현재 촬영 시간대를 담으며, 빈 비현재 시간대는 제외한다. "
                    + "slots[].uploaded는 요청 유저 본인 기준이며, 지난 슬롯 + uploaded=false는 '놓침'으로 판정하면 된다. "
                    + "참여자가 아니면 403(ROOM_403_NOT_MEMBER), 방이 없으면 404(ROOM_404_NOT_FOUND).")
    @GetMapping("/{roomId}/today")
    public ApiResponse<TodayResponse> getToday(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomTodayService.getToday(roomId, userId));
    }

    @Operation(summary = "방 멤버 목록 조회",
            description = "방 참여자 목록을 반환한다. nickname은 기본 가명(미배정 시 익명)이며, 본인·상호매칭 상대만 닉네임을 공개한다(name_revealed=true). "
                    + "gender는 항상 공개, location·age는 단건 프로필과 동일하게 "
                    + "3일차부터 공개한다(그 전에는 null, 본인 항목은 예외). 나머지 Day 게이트 항목은 멤버 단건 프로필 조회에서 다룬다. "
                    + "참여자가 아니면 403(ROOM_403_NOT_MEMBER), 방이 없으면 404(ROOM_404_NOT_FOUND).")
    @GetMapping("/{roomId}/members")
    public ApiResponse<MembersResponse> getMembers(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                   Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomMembersService.getMembers(roomId, userId));
    }

    @Operation(summary = "방 멤버 단건 프로필 조회",
            description = "같은 방 멤버(room_member_id)의 프로필을 반환한다. nickname은 기본 가명(미배정 시 익명)이며, "
                    + "본인·상호매칭 상대만 닉네임을 공개한다(name_revealed=true). 나머지 항목별 공개는 current_day 기반 자동 공개이며, "
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
            description = "마지막 날(Day3) 최종선택을 확정한다. 멤버당 1회만 가능(변경 불가)이며, 동시공개라 제출 시점엔 "
                    + "매칭 성사 여부를 알 수 없다(응답 submitted=true만 반환) — 결과는 result_at 이후 매칭 결과 조회로 공개된다. "
                    + "요청 body의 selected_member_id가 null이면 기권 처리된다. "
                    + "최종선택 시간 전이면 409(ROOM_409_NOT_FINAL_DAY), 자기 자신 선택은 400(ROOM_400_CANNOT_SELECT_SELF), "
                    + "동성 멤버 선택은 400(ROOM_400_CANNOT_SELECT_SAME_GENDER), "
                    + "이미 선택했으면 409(ROOM_409_ALREADY_SELECTED), 대상이 같은 방 멤버가 아니면 404(ROOM_MEMBER_404_NOT_FOUND), "
                    + "참여자가 아니면 403(ROOM_403_NOT_MEMBER).")
    @PostMapping("/{roomId}/final-selection")
    public ApiResponse<FinalSelectionResponse> selectFinal(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                           @Valid @RequestBody FinalSelectionRequest request,
                                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(finalSelectionService.select(roomId, request.selectedMemberId(), userId));
    }

    @Operation(summary = "내 최종선택 조회",
            description = "내가 이 방에서 제출한 최종선택 상태를 반환한다(방 재진입 시 화면 복원용). 미제출이면 404가 아닌 "
                    + "submitted=false로 응답하고, 기권 제출이면 submitted=true·selected_member_id=null이다. "
                    + "발표 전 결과 유출 방지를 위해 상호매칭 여부(matched)는 포함하지 않는다. 참여자가 아니면 403(ROOM_403_NOT_MEMBER).")
    @GetMapping("/{roomId}/final-selection")
    public ApiResponse<MyFinalSelectionResponse> getMyFinalSelection(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                                     Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(finalSelectionService.getMySelection(roomId, userId));
    }

    @Operation(summary = "내 매칭 결과 조회",
            description = "상호선택으로 성사된 내 매칭 상대의 방 멤버 id(partner_member_id)와 성사 시각(matched_at)을 반환한다. "
                    + "chat_room_id는 상호매칭 직후 채팅방 자동 생성 이벤트 처리 전까지 null일 수 있다(수 초 내 생성 — "
                    + "null이면 채팅 탭 이동으로 대체하거나 재조회). "
                    + "참여자가 아니면 403(ROOM_403_NOT_MEMBER), 성사된 매칭이 없으면 404(ROOM_MATCH_404_NOT_FOUND) — "
                    + "이 404는 클라이언트가 '매칭 미성사' 정상 분기로 사용하는 계약이므로 코드 변경 금지.")
    @GetMapping("/{roomId}/match-result")
    public ApiResponse<MatchResultResponse> getMatchResult(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                                           Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(matchResultService.getMatchResult(roomId, userId));
    }

    @Operation(summary = "현재 슬롯 창 영상 피드 조회",
            description = "지금 노출 중인 슬롯 창(slot_start 기준 최근 3시간)의 영상 피드를 반환한다. "
                    + "slots는 공개 가능한 영상이 있는 시간대와 ACTIVE 현재 촬영 시간대를 담으며 locked는 하위 호환 필드로 항상 false다. "
                    + "현재 촬영 슬롯 여부(current, dots 촬영 CTA 대상 — ACTIVE일 때만 true), "
                    + "각 영상은 반응 집계(reaction_counts: [{type, emoji, count}] 배열 — 반응 0인 이모지 타입은 생략, ReactionType 선언 순서)와 "
                    + "내 반응(my_reaction, 없으면 null)을 함께 담는다. 참여자가 아니면 403(ROOM_403_NOT_MEMBER).")
    @GetMapping("/{roomId}/feed")
    public ApiResponse<FeedResponse> getFeed(@Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
                                             @Parameter(description = "과거 회고 커서(#350). 이 slot_start 이전 슬롯을 조회한다. "
                                                     + "빈 시간대를 포함한 원본 시간축 기준이며, 생략 시 현재 노출 창(최근 3시간)이다. "
                                                     + "응답의 next_cursor 를 넣어 이어 조회한다.",
                                                     example = "2026-06-26T20:00:00")
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
                                             @Parameter(description = "과거 페이지 슬롯 수(기본 6, 최대 24). before 지정 시에만 적용.", example = "6")
                                             @RequestParam(defaultValue = "6") int size,
                                             Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(before == null
                ? roomFeedService.getFeed(roomId, userId)
                : roomFeedService.getFeedBefore(roomId, userId, before, size));
    }

    @Operation(summary = "현재 열린 미션 카드 조회",
            description = "지금 열려 있는(opens_at ≤ now < closes_at) 방 미션 카드 목록을 sequence 오름차순으로 반환한다. "
                    + "미션은 방 시작 시 자동 배정되며, 아직 열리지 않았거나 마감된 미션은 제외된다. 영상 업로드와 독립 카드다. "
                    + "참여자가 아니면 403(ROOM_403_NOT_MEMBER).")
    @GetMapping("/{roomId}/missions")
    public ApiResponse<List<RoomMissionResponse>> getMissions(
            @Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomMissionService.getOpenMissions(roomId, userId));
    }

    @Operation(summary = "미션 카드 노출 기록", description = "열린 미션 대상 멤버의 최초·최근 노출 시각을 멱등 기록한다.")
    @PostMapping("/{roomId}/missions/{roomMissionId}/impression")
    public ApiResponse<Void> impressMission(
            @PathVariable Long roomId,
            @PathVariable Long roomMissionId,
            Authentication authentication) {
        missionMemberStateService.impress(roomId, roomMissionId, (Long) authentication.getPrincipal());
        return ApiResponse.ok(null);
    }

    @Operation(summary = "미션 카드 닫기", description = "열린 미션 카드를 해당 멤버에게만 멱등 해소한다.")
    @PostMapping("/{roomId}/missions/{roomMissionId}/dismiss")
    public ApiResponse<Void> dismissMission(
            @PathVariable Long roomId,
            @PathVariable Long roomMissionId,
            Authentication authentication) {
        missionMemberStateService.dismiss(roomId, roomMissionId, (Long) authentication.getPrincipal());
        return ApiResponse.ok(null);
    }
}
