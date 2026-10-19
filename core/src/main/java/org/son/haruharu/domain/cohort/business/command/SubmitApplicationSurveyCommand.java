package org.son.haruharu.domain.cohort.business.command;

import org.son.haruharu.domain.cohort.implement.ApplicationSurvey;
import org.son.haruharu.domain.cohort.implement.FriendPreference;

/** 결제 뒤 설문 제출·수정. */
public record SubmitApplicationSurveyCommand(String referralSource, FriendPreference friendPreference,
                                             String motivation) {

    public ApplicationSurvey toSurvey() {
        return new ApplicationSurvey(referralSource, friendPreference, motivation);
    }
}
