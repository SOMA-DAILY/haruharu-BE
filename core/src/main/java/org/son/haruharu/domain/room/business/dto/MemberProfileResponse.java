package org.son.haruharu.domain.room.business.dto;

import java.util.List;

/**
 * 방 멤버 단건 프로필. 닉네임·성별은 항상 노출(Day1). 나머지 항목(fields)은 currentDay >= availableFromDay면
 * 전원에게 자동 공개된다(재화·해금 없음). 가치관 답변(valueAnswers)은 Day2부터 공개.
 * visible=false면 value/answer는 null로 가려진다.
 * profileImageUrl은 대표사진 presigned GET URL(단기 만료). 대표사진 미수집이면 null이다(온보딩에서 이미지를 받지 않을 수 있음 —
 * MembersResponse·MatchResultResponse와 동일 정책). imageRevealed=false면 클라이언트가 블러 처리하고, 최종 상호매칭 성립(또는 본인)
 * 이후 true가 된다. imageRevealed=true여도 URL이 null일 수 있으므로 클라이언트는 placeholder로 렌더한다.
 */
public record MemberProfileResponse(
        Long roomMemberId,
        String nickname,
        String gender,
        String profileImageUrl,
        boolean imageRevealed,
        List<ProfileFieldView> fields,
        List<ValueAnswerView> valueAnswers
) {

    public record ProfileFieldView(
            String key,
            String label,
            int availableFromDay,
            boolean visible,
            String value
    ) {}

    /** answerText는 answer(A/B)를 질문의 option_a/option_b 텍스트로 변환한 값(응답 시점 조인, visible=false면 null). */
    public record ValueAnswerView(
            Long answerId,
            Long questionId,
            String questionText,
            boolean visible,
            String answer,
            String answerText
    ) {}
}
