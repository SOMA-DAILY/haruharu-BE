package org.son.haruharu.admin.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.ApplicationReviewReason;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.FriendPreference;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 기수 신청자 표 한 줄(#515). */
public record AdminCohortApplicationResponse(
        @Schema(description = "신청 id", example = "12") Long applicationId,
        @Schema(description = "유저 id", example = "901") Long userId,
        @Schema(description = "상태", example = "QUEUED") CohortApplicationStatus status,
        @Schema(description = "확인 필요 사유. 없으면 null", nullable = true) ApplicationReviewReason reviewReason,
        @Schema(description = "PASS 이름. PASS 전이면 null", nullable = true, example = "김하루") String name,
        @Schema(description = "성별(PASS 판정 뒤 값). PASS 전이면 null", nullable = true) ApplicantGender gender,
        @Schema(description = "연 나이(올해 − 출생연도, 트랙 판정과 같은 기준). PASS 전이면 null", nullable = true, example = "23")
        Integer age,
        @Schema(description = "학교", example = "한국대학교") String school,
        @Schema(description = "학과", example = "컴퓨터공학과") String department,
        @Schema(description = "신청 때 적은 번호(숫자만). 운영진이 웹 신청자와 대조·연락하는 값이라 가리지 않는다",
                nullable = true, example = "01012345678") String phone,
        @Schema(description = "트랙 추가 문항의 답(문항 key → 답)") Map<String, String> answers,
        @Schema(description = "설문: 알게 된 경로. 설문 전이면 null", nullable = true) String referralSource,
        @Schema(description = "설문: 친구 신청 여부. 설문 전이면 null", nullable = true) FriendPreference friendPreference,
        @Schema(description = "설문: 신청 마음", nullable = true) String motivation,
        @Schema(description = "설문 낸 시각. 설문 전이면 null", nullable = true) LocalDateTime surveySubmittedAt,
        @Schema(description = "편성 우선순위. 직전 신청이 미배정으로 닫혔으면 1", example = "0") int priority,
        @Schema(description = "신청 시각") LocalDateTime appliedAt,
        @Schema(description = "편성 대기가 된 시각", nullable = true) LocalDateTime queuedAt,
        @Schema(description = "배정된 방 id", nullable = true) Long roomId,
        @Schema(description = "들어갔던 방 id(최근부터, 배정된 방 제외). 같은 방이었던 사람 재조합을 피하는 데 쓴다")
        List<Long> pastRoomIds,
        @Schema(description = "닫힌 이유", nullable = true) ApplicationCloseReason closeReason,
        @Schema(description = "운영진 메모", nullable = true) String adminMemo
) {
}
