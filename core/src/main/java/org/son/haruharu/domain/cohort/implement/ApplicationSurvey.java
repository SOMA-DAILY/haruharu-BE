package org.son.haruharu.domain.cohort.implement;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortApplicationException;

import java.util.ArrayList;
import java.util.List;

/**
 * 결제 뒤 설문. 설문 자체가 선택이라 앱에서 [나중에]로 건너뛸 수 있고, 신청이 진행 중이면 언제든 제출·수정한다.
 * 낼 때는 유입 경로·친구 응답이 필수, 신청 마음은 선택이다.
 */
public record ApplicationSurvey(String referralSource, FriendPreference friendPreference, String motivation) {

    static final int REFERRAL_SOURCE_MAX = 40;
    static final int MOTIVATION_MAX = 300;

    public ApplicationSurvey validated() {
        List<ApiFieldError> errors = new ArrayList<>();
        String referral = trim(referralSource);
        if (referral == null || referral.length() > REFERRAL_SOURCE_MAX) {
            errors.add(new ApiFieldError("referral_source", "알게 된 경로를 1~" + REFERRAL_SOURCE_MAX + "자로 골라 주세요."));
        }
        if (friendPreference == null) {
            errors.add(new ApiFieldError("friend_preference", "친구 신청 여부를 골라 주세요."));
        }
        String trimmedMotivation = trim(motivation);
        if (trimmedMotivation != null && trimmedMotivation.length() > MOTIVATION_MAX) {
            errors.add(new ApiFieldError("motivation", "신청 마음은 " + MOTIVATION_MAX + "자까지 쓸 수 있어요."));
        }
        if (!errors.isEmpty()) throw new InvalidCohortApplicationException(errors);
        return new ApplicationSurvey(referral, friendPreference, trimmedMotivation);
    }

    private static String trim(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
