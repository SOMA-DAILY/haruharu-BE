package org.son.haruharu.domain.room.business.dto;

import java.util.List;

/**
 * 방 멤버 단건 프로필. 성별은 항상 노출(Day1). nickname 필드에는 표시 이름이 담긴다(#393): 본인·상호매칭이면 실명(닉네임),
 * 그 전에는 방 세션 가명이며 nameRevealed로 구분한다(imageRevealed와 동일 게이트). 나머지 항목(fields)은
 * currentDay >= availableFromDay면 전원에게 자동 공개된다(재화·해금 없음). 가치관 답변(valueAnswers)은 Day2부터 공개.
 * visible=false면 value/answer는 null로 가려진다.
 * profileImageUrl은 대표사진 presigned GET URL(단기 만료). 대표사진 미수집이면 null이다(온보딩에서 이미지를 받지 않을 수 있음 —
 * MembersResponse·MatchResultResponse와 동일 정책). imageRevealed=false면 클라이언트가 블러 처리하고, 최종 상호매칭 성립(또는 본인)
 * 이후 true가 된다. imageRevealed=true여도 URL이 null일 수 있으므로 클라이언트는 placeholder로 렌더한다.
 */
public record MemberProfileResponse(
        Long roomMemberId,
        String nickname,
        boolean nameRevealed,
        String gender,
        String profileImageUrl,
        boolean imageRevealed,
        List<ProfileFieldView> fields,
        List<ValueAnswerView> valueAnswers
) {

    /**
     * 항목 1줄. key 는 MBTI, INTO_NOW, AGE, DEPARTMENT(#457, 시트 표시 순서). value 는 항상 문자열(AGE 도 "23").
     * reveal_at(#457 추가) = 편성상 공개 시각(없으면 null), available_from_day 는 그 시각의 DAY(달력 날짜 기준, 옛 방은 3).
     */
    public record ProfileFieldView(
            String key,
            String label,
            int availableFromDay,
            boolean visible,
            String value,
            java.time.LocalDateTime revealAt
    ) {
        public ProfileFieldView(String key, String label, int availableFromDay, boolean visible, String value) {
            this(key, label, availableFromDay, visible, value, null);
        }
    }

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
