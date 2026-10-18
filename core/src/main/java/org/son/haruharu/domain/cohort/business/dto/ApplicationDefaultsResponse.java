package org.son.haruharu.domain.cohort.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.profile.implement.UserProfile;

/**
 * 신청서 입력칸에 미리 채울 값(본인에게만 준다). 학교·학과는 직전 신청서를 먼저 쓴다 —
 * 프로필 affiliation 은 마이페이지 "하는 일" 칸이라 직업이 들어 있을 수 있다. 없으면 프로필 값, 번호는 프로필 값.
 */
public record ApplicationDefaultsResponse(
        @Schema(description = "학교. 직전 신청서 → 프로필 affiliation 순. 없으면 null", nullable = true, example = "한국대학교")
        String school,
        @Schema(description = "학과. 직전 신청서 → 프로필 학과 순. 없으면 null", nullable = true, example = "컴퓨터공학과")
        String department,
        @Schema(description = "휴대전화 번호(숫자만). 프로필 값. 없으면 null", nullable = true, example = "01012345678")
        String phone
) {
    public static ApplicationDefaultsResponse of(CohortApplication latest, UserProfile profile) {
        String school = latest != null ? latest.school() : profile != null ? profile.affiliation() : null;
        String department = latest != null ? latest.department() : profile != null ? profile.department() : null;
        return new ApplicationDefaultsResponse(school, department, profile != null ? profile.phone() : null);
    }
}
