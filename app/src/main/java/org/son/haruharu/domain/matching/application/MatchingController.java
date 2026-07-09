package org.son.haruharu.domain.matching.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.matching.business.MatchingService;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.son.haruharu.domain.matching.business.dto.QueueStatusResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "매칭", description = "매칭 대기열 참가·조회·취소 (하루 1회 신청, 입장 코인 차감)")
@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @Operation(summary = "내 매칭 대기 상태 조회",
            description = "내 매칭 대기 상태를 반환한다. 대기 중이면 큐 정보를, 대기 중이 아니면 none 상태를 반환한다.")
    @GetMapping("/queue")
    public ApiResponse<QueueStatusResponse> getQueueStatus(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(matchingService.getQueueStatus(userId));
    }

    @Operation(summary = "매칭 대기열 참가",
            description = "매칭 대기열에 참가하며 입장 코인을 차감한다. "
                    + "신청 마감 시간대(21:00~자정)면 409(MATCHING_CLOSED), 본인인증 미완료면 403(MATCHING_403_NOT_VERIFIED), "
                    + "진행 중인 소개팅방이 있으면 409(MATCHING_ROOM_IN_PROGRESS), 이미 신청한 상태면 409(MATCHING_409_ALREADY_QUEUED). "
                    + "코인 잔액 부족 시 롤백되어 큐 등록도 취소된다.")
    @PostMapping("/queue")
    public ApiResponse<QueueResponse> joinQueue(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(matchingService.joinQueue(userId));
    }

    @Operation(summary = "매칭 대기 취소",
            description = "매칭 대기를 취소하고 차감된 입장 코인을 환불한다. 취소할 대기 내역이 없으면 404(MATCHING_404_NOT_QUEUED).")
    @DeleteMapping("/queue")
    public ApiResponse<Void> leaveQueue(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        matchingService.leaveQueue(userId);
        return ApiResponse.ok(null);
    }
}
