package org.son.haruharu.domain.value.business.dto;

import org.son.haruharu.domain.value.implement.ValueQuestion;

import java.util.List;
import java.util.Map;

public record MyValueAnswersResponse(List<AnswerItem> answers) {

    public record AnswerItem(
            Long questionId,
            String questionText,
            String optionA,
            String optionB,
            String answer
    ) {}

    /** 활성 질문 전체에 내 답변을 매핑한다. 미응답 질문의 answer는 null(수정 화면 프리필용). */
    public static MyValueAnswersResponse of(List<ValueQuestion> activeQuestions, Map<Long, String> answerByQuestion) {
        List<AnswerItem> items = activeQuestions.stream()
                .map(q -> new AnswerItem(q.id(), q.questionText(), q.optionA(), q.optionB(), answerByQuestion.get(q.id())))
                .toList();
        return new MyValueAnswersResponse(items);
    }
}
