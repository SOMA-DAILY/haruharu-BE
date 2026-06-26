package org.son.haruharu.domain.room.business.dto;

import java.util.List;
import java.util.UUID;

/**
 * 방 멤버 단건 프로필. 기본 프로필(직업/학교·키·MBTI)은 Day3부터 공개,
 * 가치관 답변은 열쇠로 해금한 것만 내용이 보인다(미해금은 answer=null).
 */
public record MemberProfileResponse(
        UUID roomMemberId,
        String nickname,
        String gender,
        String location,
        Integer age,
        String affiliation,
        Integer height,
        String mbti,
        List<ValueAnswerView> valueAnswers
) {

    public record ValueAnswerView(
            UUID answerId,
            UUID questionId,
            String questionText,
            int unlockCost,
            boolean unlocked,
            String answer
    ) {}
}
