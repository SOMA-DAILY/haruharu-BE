package org.son.haruharu.domain.cohort.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ApplicationSurveyTest {

    @Test
    @DisplayName("유입 경로·친구 응답은 필수, 신청 마음은 비우면 null 로 저장한다")
    void givenSurvey_whenValidated_thenTrimmed() {
        ApplicationSurvey survey = new ApplicationSurvey(" 인스타그램 ", FriendPreference.SAME_ROOM, "  ").validated();

        assertThat(survey.referralSource()).isEqualTo("인스타그램");
        assertThat(survey.friendPreference()).isEqualTo(FriendPreference.SAME_ROOM);
        assertThat(survey.motivation()).isNull();
    }

    @Test
    @DisplayName("빠진 필수 칸과 너무 긴 신청 마음을 모아서 알려준다")
    void givenInvalidSurvey_whenValidated_thenFieldErrors() {
        InvalidCohortApplicationException e = catchThrowableOfType(InvalidCohortApplicationException.class,
                () -> new ApplicationSurvey(null, null, "가".repeat(301)).validated());

        assertThat(e.getFieldErrors()).extracting(ApiFieldError::field)
                .containsExactly("referral_source", "friend_preference", "motivation");
    }
}
