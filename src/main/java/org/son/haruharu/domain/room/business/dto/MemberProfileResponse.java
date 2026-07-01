package org.son.haruharu.domain.room.business.dto;

import java.util.List;
import java.util.UUID;

/**
 * 방 멤버 단건 프로필. 기본 항목(닉네임·성별·지역·나이)은 항상 노출.
 * unlockableFields는 3-state(잠김/해금가능/해금) — unlockable·unlocked·value 3필드로 표현한다.
 */
public record MemberProfileResponse(
        UUID roomMemberId,
        String nickname,
        String gender,
        String location,
        Integer age,
        List<UnlockableFieldView> unlockableFields,
        List<ValueAnswerView> valueAnswers
) {

    public record UnlockableFieldView(
            String fieldKey,
            String label,
            int unlockCost,
            int availableFromDay,
            boolean unlockable,
            boolean unlocked,
            String value
    ) {}

    public record ValueAnswerView(
            UUID answerId,
            UUID questionId,
            String questionText,
            int unlockCost,
            boolean unlocked,
            String answer
    ) {}
}
