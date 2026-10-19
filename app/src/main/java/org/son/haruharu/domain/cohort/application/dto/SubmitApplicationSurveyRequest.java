package org.son.haruharu.domain.cohort.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.cohort.business.command.SubmitApplicationSurveyCommand;
import org.son.haruharu.domain.cohort.implement.FriendPreference;

public record SubmitApplicationSurveyRequest(
        @Schema(description = "알게 된 경로(필수, 1~40자)", example = "인스타그램") String referralSource,
        @Schema(description = "친구 신청 여부(필수). ALONE·SAME_ROOM·DIFFERENT_ROOM·ANY", example = "ALONE")
        FriendPreference friendPreference,
        @Schema(description = "신청 마음(선택, 300자까지)", nullable = true, example = "새로운 사람을 만나고 싶어요") String motivation
) {
    public SubmitApplicationSurveyCommand toCommand() {
        return new SubmitApplicationSurveyCommand(referralSource, friendPreference, motivation);
    }
}
