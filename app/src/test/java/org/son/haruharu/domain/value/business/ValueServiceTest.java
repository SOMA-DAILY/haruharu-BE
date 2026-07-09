package org.son.haruharu.domain.value.business;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.value.business.command.SaveValueAnswersCommand;
import org.son.haruharu.domain.value.business.dto.SavedAnswersResponse;
import org.son.haruharu.domain.value.business.dto.ValueQuestionsResponse;
import org.son.haruharu.domain.value.implement.UserValueAnswer;
import org.son.haruharu.domain.value.implement.UserValueAnswerReader;
import org.son.haruharu.domain.value.implement.UserValueAnswerWriter;
import org.son.haruharu.domain.value.implement.ValueQuestion;
import org.son.haruharu.domain.value.implement.ValueQuestionReader;
import org.son.haruharu.domain.value.storage.exception.ValueQuestionNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ValueServiceTest {

    @Mock ValueQuestionReader valueQuestionReader;
    @Mock UserValueAnswerReader userValueAnswerReader;
    @Mock UserValueAnswerWriter userValueAnswerWriter;

    ValueService valueService;

    @BeforeEach
    void setUp() {
        valueService = new ValueService(valueQuestionReader, userValueAnswerReader, userValueAnswerWriter);
    }

    @Test
    @DisplayName("활성 질문 조회 시 Reader가 반환한 질문 목록을 응답으로 변환한다")
    void givenActiveQuestions_whenGetActiveQuestions_thenReturnsMappedResponse() {
        // given
        Long questionId = 1L;
        given(valueQuestionReader.getAllActive()).willReturn(List.of(
                new ValueQuestion(questionId, "주말엔 집 vs 밖, 어느 쪽?", "집순이", "밖순이", "LIFESTYLE", 1, true)
        ));

        // when
        ValueQuestionsResponse response = valueService.getActiveQuestions();

        // then
        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().get(0).questionId()).isEqualTo(questionId);
        assertThat(response.questions().get(0).questionText()).isEqualTo("주말엔 집 vs 밖, 어느 쪽?");
        assertThat(response.questions().get(0).optionA()).isEqualTo("집순이");
        assertThat(response.questions().get(0).optionB()).isEqualTo("밖순이");
        assertThat(response.questions().get(0).category()).isEqualTo("LIFESTYLE");
        assertThat(response.questions().get(0).displayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("답변 일괄 저장 시 각 답변을 신규 INSERT하고 저장 개수를 반환한다")
    void givenAnswers_whenSaveAnswers_thenSavesEachAndReturnsCount() {
        // given
        Long userId = 1L;
        Long q1 = 1L;
        Long q2 = 2L;
        var command = new SaveValueAnswersCommand(List.of(
                new SaveValueAnswersCommand.AnswerItem(q1, "A"),
                new SaveValueAnswersCommand.AnswerItem(q2, "B")
        ));
        given(valueQuestionReader.getAllActive()).willReturn(List.of(
                new ValueQuestion(q1, "질문1", "A", "B", "LIFESTYLE", 1, true),
                new ValueQuestion(q2, "질문2", "A", "B", "LIFESTYLE", 2, true)
        ));
        given(userValueAnswerReader.getAllByUserId(userId)).willReturn(List.of());

        // when
        SavedAnswersResponse response = valueService.saveAnswers(userId, command);

        // then
        assertThat(response.savedCount()).isEqualTo(2);

        ArgumentCaptor<UserValueAnswer> captor = ArgumentCaptor.forClass(UserValueAnswer.class);
        then(userValueAnswerWriter).should(times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(saved -> {
                    assertThat(saved.id()).isNull();
                    assertThat(saved.userId()).isEqualTo(userId);
                })
                .extracting(UserValueAnswer::questionId)
                .containsExactly(q1, q2);
    }

    @Test
    @DisplayName("이미 답변한 questionId 재제출 시 기존 답변 id로 갱신한다(멱등)")
    void givenExistingAnswer_whenSaveAnswers_thenUpdatesSameId() {
        // given
        Long userId = 1L;
        Long questionId = 1L;
        Long existingAnswerId = 100L;
        var command = new SaveValueAnswersCommand(List.of(
                new SaveValueAnswersCommand.AnswerItem(questionId, "B")
        ));
        given(valueQuestionReader.getAllActive()).willReturn(List.of(
                new ValueQuestion(questionId, "질문", "A", "B", "LIFESTYLE", 1, true)
        ));
        given(userValueAnswerReader.getAllByUserId(userId)).willReturn(List.of(
                new UserValueAnswer(existingAnswerId, userId, questionId, "이전 답변")
        ));

        // when
        valueService.saveAnswers(userId, command);

        // then
        ArgumentCaptor<UserValueAnswer> captor = ArgumentCaptor.forClass(UserValueAnswer.class);
        then(userValueAnswerWriter).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(existingAnswerId);
        assertThat(captor.getValue().answer()).isEqualTo("B");
    }

    @Test
    @DisplayName("존재하지 않는 questionId로 답변 저장 시 ValueQuestionNotFoundException이 발생한다")
    void givenNonExistentQuestionId_whenSaveAnswers_thenThrowsAndDoesNotSave() {
        // given
        Long userId = 1L;
        Long unknown = 999L;
        var command = new SaveValueAnswersCommand(List.of(
                new SaveValueAnswersCommand.AnswerItem(unknown, "A")
        ));
        given(valueQuestionReader.getAllActive()).willReturn(List.of());
        given(userValueAnswerReader.getAllByUserId(userId)).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> valueService.saveAnswers(userId, command))
                .isInstanceOf(ValueQuestionNotFoundException.class);
        then(userValueAnswerWriter).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}
