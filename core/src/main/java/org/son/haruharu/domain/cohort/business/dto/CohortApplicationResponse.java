package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.FriendPreference;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;

import java.time.LocalDateTime;
import java.util.Map;

/** 앱에 보이는 내 기수 신청. 확인 필요 사유·운영진 메모는 내리지 않는다. */
public record CohortApplicationResponse(
        @Schema(description = "신청 id", example = "1") Long applicationId,
        @Schema(description = "기수 id", example = "3") Long cohortId,
        @Schema(description = "기수 이름", example = "서울 대학생 6기") String cohortName,
        @Schema(description = "진행 기간 표기", example = "10월 마지막 주") String periodLabel,
        @Schema(description = "트랙 키", example = "seoul-student") String trackKey,
        @Schema(description = "상태. IDENTITY_PENDING(PASS 대기)·NEEDS_REVIEW(운영진 확인 중)·QUEUED(편성 대기)·"
                + "ASSIGNED(방 배정)·COMPLETED(완료)·CANCELLED(본인 취소)·NOT_ASSIGNED(미배정 종료)",
                example = "QUEUED") CohortApplicationStatus status,
        @Schema(description = "학교", example = "한국대학교") String school,
        @Schema(description = "학과", example = "컴퓨터공학과") String department,
        @Schema(description = "트랙 추가 문항의 답(문항 key → 답)") Map<String, String> answers,
        @Schema(description = "설문: 알게 된 경로. 설문 전이면 null", nullable = true, example = "인스타그램") String referralSource,
        @Schema(description = "설문: 친구 신청 여부. 설문 전이면 null", nullable = true, example = "ALONE")
        FriendPreference friendPreference,
        @Schema(description = "설문: 신청 마음. 없으면 null", nullable = true) String motivation,
        @Schema(description = "설문을 마지막으로 낸 시각. 설문 전이면 null(앱이 [나중에] 이후 다시 띄운다)", nullable = true)
        LocalDateTime surveySubmittedAt,
        @Schema(description = "신청 시각") LocalDateTime appliedAt,
        @Schema(description = "닫힌 시각. 진행 중이면 null", nullable = true) LocalDateTime closedAt,
        @Schema(description = "닫힌 이유. USER_CANCELLED·COHORT_CANCELLED 등. 진행 중이면 null", nullable = true)
        ApplicationCloseReason closeReason,
        @Schema(description = "참가권을 돌려받았는지", example = "false") boolean refunded,
        @Schema(description = "지금 앱에서 취소할 수 있는지(배정 전만)", example = "true") boolean isCancellable
) {
    public static CohortApplicationResponse of(CohortApplication application, Cohort cohort, RecruitTrack track) {
        return new CohortApplicationResponse(application.id(), cohort.id(), cohort.displayName(track.label()),
                cohort.periodLabel(), track.key(), application.status(), application.school(),
                application.department(), application.answers(), application.referralSource(),
                application.friendPreference(), application.motivation(), application.surveySubmittedAt(),
                application.appliedAt(), application.closedAt(), application.closeReason(),
                application.refundedAt() != null, application.cancellableByApplicant());
    }
}
