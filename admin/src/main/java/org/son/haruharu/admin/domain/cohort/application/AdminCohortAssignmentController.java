package org.son.haruharu.admin.domain.cohort.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.admin.domain.cohort.application.dto.AssignApplicationsRequest;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.cohort.business.CohortAssignmentService;
import org.son.haruharu.domain.cohort.business.dto.AssignedMemberResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin Cohort Assignment", description = "방에 넣기(#516). SUPER·OPERATOR")
@RestController
public class AdminCohortAssignmentController {

    private final CohortAssignmentService assignmentService;

    public AdminCohortAssignmentController(CohortAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Operation(summary = "방에 넣기", description = "편성 대기 신청자를 방 멤버로 넣고 신청을 ASSIGNED 로 바꾼다. 하나라도 안 되면 전부 거절. "
            + "없는 방 404 ROOM_NOT_FOUND, 없는 신청 404 COHORT_APPLICATION_NOT_FOUND, "
            + "기수 없음·끝남·취소된 방 409 ROOM_NOT_ASSIGNABLE, 편성 대기 아님·PASS 성별 불일치·다른 기수·이미 멤버·탈퇴/정지 유저 "
            + "409 COHORT_APPLICATION_NOT_ASSIGNABLE, 성별 정원 초과 409 ROOM_GENDER_CAPACITY_EXCEEDED. 참가권은 건드리지 않는다.")
    @PostMapping("/api/v1/admin/rooms/{roomId}/applications")
    public ApiResponse<List<AssignedMemberResponse>> assign(
            @AuthenticationPrincipal Long adminId,
            @Parameter(description = "방 id", example = "81") @PathVariable Long roomId,
            @RequestBody @Valid AssignApplicationsRequest request) {
        return ApiResponse.ok(assignmentService.assign(roomId, request.applicationIds(), "admin:" + adminId));
    }
}
