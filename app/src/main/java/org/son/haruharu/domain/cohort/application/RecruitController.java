package org.son.haruharu.domain.cohort.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.cohort.business.RecruitTrackService;
import org.son.haruharu.domain.cohort.business.dto.RecruitingTracksResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기수 신청", description = "모집 트랙·기수 조회(#510)")
@RestController
@RequestMapping("/api/v1/recruit")
// haruharu.cohort.enabled=true 일 때만 빈 등록(#510). 기본 false 라 엔드포인트 부재(404).
@ConditionalOnProperty(prefix = "haruharu.cohort", name = "enabled", havingValue = "true")
public class RecruitController {

    private final RecruitTrackService recruitTrackService;

    public RecruitController(RecruitTrackService recruitTrackService) {
        this.recruitTrackService = recruitTrackService;
    }

    @Operation(summary = "모집 중인 트랙 목록",
            description = "공개(OPEN)된 기수가 있는 활성 트랙을 표시 순서대로 준다. 각 트랙에 열린 기수와 트랙별 추가 문항을 담는다. "
                    + "신청 시작 전·마감 후의 기수도 내려가며 cohort.is_accepting 으로 지금 신청할 수 있는지 구분한다.")
    @GetMapping("/tracks")
    public ApiResponse<RecruitingTracksResponse> getTracks() {
        return ApiResponse.ok(recruitTrackService.getRecruitingTracks());
    }
}
