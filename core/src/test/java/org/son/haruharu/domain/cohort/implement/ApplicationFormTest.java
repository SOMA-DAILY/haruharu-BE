package org.son.haruharu.domain.cohort.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortApplicationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ApplicationFormTest {

    private static final List<TrackQuestion> QUESTIONS = List.of(
            new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE, true, List.of("1학년", "2학년", "3학년")),
            new TrackQuestion("hobby", "취미", TrackQuestionType.TEXT, false, List.of()));

    private static List<String> fields(InvalidCohortApplicationException e) {
        return e.getFieldErrors().stream().map(ApiFieldError::field).toList();
    }

    @Test
    @DisplayName("앞뒤 공백을 지우고 번호는 숫자만 남기며, 비어 있는 선택 문항 답은 저장하지 않는다")
    void givenValidForm_whenValidated_thenNormalized() {
        ApplicationForm form = new ApplicationForm(" 한국대 ", " 컴퓨터공학과 ", "010-1234-5678",
                Map.of("grade", " 3학년 ", "hobby", "  "));

        ApplicationForm validated = form.validated(QUESTIONS);

        assertThat(validated.school()).isEqualTo("한국대");
        assertThat(validated.department()).isEqualTo("컴퓨터공학과");
        assertThat(validated.phone()).isEqualTo("01012345678");
        assertThat(validated.answers()).containsExactly(Map.entry("grade", "3학년"));
    }

    @Test
    @DisplayName("학교·학과·번호 칸 오류를 모아서 알려준다")
    void givenInvalidBasics_whenValidated_thenCollectsFieldErrors() {
        ApplicationForm form = new ApplicationForm(" ", "가".repeat(16), "02-123-4567", Map.of("grade", "1학년"));

        InvalidCohortApplicationException e = catchThrowableOfType(InvalidCohortApplicationException.class,
                () -> form.validated(QUESTIONS));

        assertThat(fields(e)).containsExactly("school", "department", "phone");
    }

    @Test
    @DisplayName("필수 문항 누락·보기 밖 답은 answers.{key}, 모르는 문항 키는 키를 싣지 않고 answers 한 줄로 알린다")
    void givenInvalidAnswers_whenValidated_thenAnswerFieldErrors() {
        Map<String, String> answers = new HashMap<>();
        answers.put("unknown", "x");
        answers.put("hobby", "가".repeat(201));
        ApplicationForm missing = new ApplicationForm("한국대", "컴퓨터공학과", "01012345678", answers);
        ApplicationForm wrongChoice = new ApplicationForm("한국대", "컴퓨터공학과", "01012345678",
                Map.of("grade", "5학년"));

        assertThat(fields(catchThrowableOfType(InvalidCohortApplicationException.class,
                () -> missing.validated(QUESTIONS))))
                .containsExactly("answers", "answers.grade", "answers.hobby");
        assertThat(fields(catchThrowableOfType(InvalidCohortApplicationException.class,
                () -> wrongChoice.validated(QUESTIONS))))
                .containsExactly("answers.grade");
    }

    @Test
    @DisplayName("문항이 없는 트랙은 답 없이 신청한다")
    void givenNoQuestions_whenValidated_thenEmptyAnswers() {
        ApplicationForm form = new ApplicationForm("한국대", "컴퓨터공학과", "01112345678", null);

        assertThat(form.validated(List.of()).answers()).isEmpty();
    }
}
