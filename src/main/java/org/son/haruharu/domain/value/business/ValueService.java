package org.son.haruharu.domain.value.business;

import org.son.haruharu.domain.value.business.command.SaveValueAnswersCommand;
import org.son.haruharu.domain.value.business.dto.SavedAnswersResponse;
import org.son.haruharu.domain.value.business.dto.ValueQuestionsResponse;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.UserValueAnswerWriter;
import org.son.haruharu.domain.value.business.exception.InvalidValueAnswerException;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.son.haruharu.domain.value.storage.exception.ValueQuestionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ValueService {

    // answer는 선택지 텍스트가 아니라 A/B 키만 저장한다(닫힌 집합). 텍스트는 질문의 option_a/option_b로 조합한다.
    private static final Set<String> ALLOWED_ANSWERS = Set.of("A", "B");

    private final ValueQuestionReader valueQuestionReader;
    private final UserValueAnswerReader userValueAnswerReader;
    private final UserValueAnswerWriter userValueAnswerWriter;

    public ValueService(
            ValueQuestionReader valueQuestionReader,
            UserValueAnswerReader userValueAnswerReader,
            UserValueAnswerWriter userValueAnswerWriter
    ) {
        this.valueQuestionReader = valueQuestionReader;
        this.userValueAnswerReader = userValueAnswerReader;
        this.userValueAnswerWriter = userValueAnswerWriter;
    }

    public ValueQuestionsResponse getActiveQuestions() {
        return ValueQuestionsResponse.from(valueQuestionReader.getAllActive());
    }

    /** 온보딩 완료 판정용: 가치관 답변이 하나라도 저장되어 있는지 여부. */
    public boolean hasAnswered(Long userId) {
        return !userValueAnswerReader.getAllByUserId(userId).isEmpty();
    }

    @Transactional
    public SavedAnswersResponse saveAnswers(Long userId, SaveValueAnswersCommand command) {
        // 질문은 참조 데이터(소량, enum 성격)이므로 활성 질문 전체와 기존 답변을 각 1회만 로드한다(N+1 제거).
        Set<Long> activeQuestionIds = valueQuestionReader.getAllActive().stream()
                .map(ValueQuestion::id)
                .collect(Collectors.toSet());
        Map<Long, Long> existingAnswerIds = userValueAnswerReader.getAllByUserId(userId).stream()
                .collect(Collectors.toMap(UserValueAnswer::questionId, UserValueAnswer::id));

        // 한 요청에 같은 questionId가 중복되면 마지막 답변만 저장한다(last-wins). 삽입 순서 유지.
        Map<Long, String> answerByQuestion = new LinkedHashMap<>();
        for (SaveValueAnswersCommand.AnswerItem item : command.answers()) {
            answerByQuestion.put(item.questionId(), item.answer());
        }

        answerByQuestion.forEach((questionId, answer) -> {
            // answer는 A/B만 허용한다(오타·미정의 값 저장 차단).
            if (!ALLOWED_ANSWERS.contains(answer)) {
                throw new InvalidValueAnswerException();
            }
            // 온보딩은 활성 질문에만 답할 수 있다. 비활성/존재하지 않는 질문이면 404.
            if (!activeQuestionIds.contains(questionId)) {
                throw new ValueQuestionNotFoundException();
            }
            // 멱등성: (user, question)당 답변은 하나만 존재한다. 기존 답변이 있으면 그 id로 갱신, 없으면 신규 생성.
            Long answerId = existingAnswerIds.get(questionId);
            userValueAnswerWriter.save(new UserValueAnswer(answerId, userId, questionId, answer));
        });
        return SavedAnswersResponse.from(answerByQuestion.size());
    }
}
