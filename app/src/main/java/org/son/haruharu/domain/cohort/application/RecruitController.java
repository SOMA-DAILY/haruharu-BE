package org.son.haruharu.domain.cohort.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.cohort.application.dto.ApplyCohortRequest;
import org.son.haruharu.domain.cohort.application.dto.SubmitApplicationSurveyRequest;
import org.son.haruharu.domain.cohort.business.CohortApplicationService;
import org.son.haruharu.domain.cohort.business.RecruitTrackService;
import org.son.haruharu.domain.cohort.business.dto.CohortApplicationResponse;
import org.son.haruharu.domain.cohort.business.dto.MyCohortApplicationResponse;
import org.son.haruharu.domain.cohort.business.dto.RecruitingTracksResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기수 신청", description = "모집 트랙·기수 조회(#510), 신청·설문·취소(#511)")
@RestController
@RequestMapping("/api/v1/recruit")
// haruharu.cohort.enabled=true 일 때만 빈 등록(#510). 기본 false 라 엔드포인트 부재(404).
@ConditionalOnProperty(prefix = "haruharu.cohort", name = "enabled", havingValue = "true")
public class RecruitController {

    private final RecruitTrackService recruitTrackService;
    private final CohortApplicationService applicationService;

    public RecruitController(RecruitTrackService recruitTrackService, CohortApplicationService applicationService) {
        this.recruitTrackService = recruitTrackService;
        this.applicationService = applicationService;
    }

    @Operation(summary = "모집 중인 트랙 목록",
            description = "공개(OPEN)된 기수가 있는 활성 트랙을 표시 순서대로 준다. 각 트랙에 열린 기수와 트랙별 추가 문항을 담는다. "
                    + "신청 시작 전·마감 후의 기수도 내려가며 cohort.is_accepting 으로 지금 신청할 수 있는지 구분한다.")
    @GetMapping("/tracks")
    public ApiResponse<RecruitingTracksResponse> getTracks() {
        return ApiResponse.ok(recruitTrackService.getRecruitingTracks());
    }

    @Operation(summary = "기수 신청",
            description = "신청하면서 참가권 1장을 바로 쓴다. 학교·학과·번호는 프로필에도 저장한다. "
                    + "PASS 를 이미 했으면 status 가 QUEUED(편성 대기)·NEEDS_REVIEW(확인 필요)로, 아니면 IDENTITY_PENDING 으로 온다. "
                    + "참가권이 없으면 400 TICKET_INSUFFICIENT 이고 신청도 남지 않는다(결제 반영이 늦으면 잠시 뒤 다시 시도). "
                    + "신청 기간 밖·공개 전·취소된 기수는 409 COHORT_NOT_ACCEPTING, 진행 중 신청이 있으면 409 "
                    + "COHORT_APPLICATION_IN_PROGRESS, 칸 오류는 400 VALIDATION_ERROR(field: school·department·phone·answers.{key}).")
    @PostMapping("/cohorts/{cohortId}/applications")
    public ApiResponse<CohortApplicationResponse> apply(
            Authentication authentication,
            @Parameter(description = "기수 id", example = "3") @PathVariable Long cohortId,
            @RequestBody ApplyCohortRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(applicationService.apply(userId, cohortId, request.toCommand()));
    }

    @Operation(summary = "내 기수 신청",
            description = "가장 최근 신청을 준다(닫힌 신청 포함). 신청한 적이 없으면 application 이 null. "
                    + "survey_submitted_at 이 null 이면 설문 전이다. defaults 는 신청서 입력칸 기본값으로, 학교·학과는 "
                    + "직전 신청서 → 프로필 순, 번호는 프로필에서 가져온다(칸마다 null 가능, 본인에게만 내려간다). "
                    + "PASS 를 마치면 PASS 대기(IDENTITY_PENDING) 신청이 편성 대기(QUEUED) 또는 확인 필요(NEEDS_REVIEW)로 "
                    + "바뀐다. PASS 직후 아직 IDENTITY_PENDING 이면 이 조회가 다시 판정해 바뀐 상태를 준다.")
    @GetMapping("/applications/me")
    public ApiResponse<MyCohortApplicationResponse> getMine(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(applicationService.getMine(userId));
    }

    @Operation(summary = "결제 뒤 설문 제출·수정",
            description = "설문 자체가 선택이다(건너뛰어도 신청은 그대로 진행). 낼 때는 referral_source·friend_preference 가 필수, "
                    + "motivation 은 선택. 진행 중인 본인 신청이면 언제든 낼 수 있고, 다시 내면 덮어쓴다. 끝난 신청은 409 COHORT_APPLICATION_CLOSED, "
                    + "남의 신청·없는 신청은 404 COHORT_APPLICATION_NOT_FOUND, 칸 오류는 400 VALIDATION_ERROR.")
    @PutMapping("/applications/{applicationId}/survey")
    public ApiResponse<CohortApplicationResponse> submitSurvey(
            Authentication authentication,
            @Parameter(description = "신청 id", example = "1") @PathVariable Long applicationId,
            @RequestBody SubmitApplicationSurveyRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(applicationService.submitSurvey(userId, applicationId, request.toCommand()));
    }

    @Operation(summary = "기수 신청 취소(배정 전)",
            description = "방 배정 전이면 취소하고 참가권 1장을 돌려준다(status CANCELLED, refunded true). "
                    + "배정 후·이미 끝난 신청은 409 COHORT_APPLICATION_NOT_CANCELLABLE.")
    @PostMapping("/applications/{applicationId}/cancel")
    public ApiResponse<CohortApplicationResponse> cancel(
            Authentication authentication,
            @Parameter(description = "신청 id", example = "1") @PathVariable Long applicationId) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(applicationService.cancel(userId, applicationId));
    }
}
