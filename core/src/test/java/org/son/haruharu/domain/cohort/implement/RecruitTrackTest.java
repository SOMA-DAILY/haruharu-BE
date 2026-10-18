package org.son.haruharu.domain.cohort.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.implement.exception.InvalidRecruitTrackException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecruitTrackTest {

    private static final TrackQuestion MBTI = new TrackQuestion("mbti", "MBTI", TrackQuestionType.TEXT, false, List.of());
    private static final TrackQuestion GRADE = new TrackQuestion(
            "grade", "학년", TrackQuestionType.CHOICE, true, List.of("1학년", "2학년", "3학년", "4학년"));

    private RecruitTrack seoulStudent() {
        return RecruitTrack.create("seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT, 20, 29,
                List.of(GRADE, MBTI), 1);
    }

    @Test
    @DisplayName("새 트랙은 활성 상태로 만들어진다")
    void givenValidInput_whenCreate_thenActive() {
        RecruitTrack track = seoulStudent();

        assertThat(track.active()).isTrue();
        assertThat(track.questions()).containsExactly(GRADE, MBTI);
    }

    @Test
    @DisplayName("키 형식·이름·나이 범위가 잘못되면 칸별로 알려준다")
    void givenInvalidFields_whenCreate_thenFieldErrors() {
        assertThatThrownBy(() -> RecruitTrack.create("Seoul Student", " ", "", null, 30, 20, List.of(), 0))
                .isInstanceOf(InvalidRecruitTrackException.class)
                .satisfies(e -> assertThat(((InvalidRecruitTrackException) e).getFieldErrors()).extracting("field")
                        .containsExactlyInAnyOrder("key", "label", "region", "audience", "max_age"));
    }

    @Test
    @DisplayName("최소 나이는 19세 이상이어야 한다")
    void givenMinorMinAge_whenCreate_thenFieldError() {
        assertThatThrownBy(() -> RecruitTrack.create("seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT,
                18, 29, List.of(), 0))
                .isInstanceOf(InvalidRecruitTrackException.class)
                .satisfies(e -> assertThat(((InvalidRecruitTrackException) e).getFieldErrors()).extracting("field")
                        .containsExactly("min_age"));
    }

    @Test
    @DisplayName("문항 키가 겹치거나 선택형인데 보기가 모자라면 해당 문항 칸을 알려준다")
    void givenInvalidQuestions_whenCreate_thenQuestionFieldErrors() {
        List<TrackQuestion> questions = List.of(
                MBTI,
                new TrackQuestion("mbti", "다시 MBTI", TrackQuestionType.TEXT, false, List.of()),
                new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE, true, List.of("1학년")),
                new TrackQuestion("memo", "메모", TrackQuestionType.TEXT, false, List.of("보기")));

        assertThatThrownBy(() -> RecruitTrack.create("seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT,
                20, 29, questions, 0))
                .isInstanceOf(InvalidRecruitTrackException.class)
                .satisfies(e -> assertThat(((InvalidRecruitTrackException) e).getFieldErrors()).extracting("field")
                        .containsExactly("questions[1].key", "questions[2].options", "questions[3].options"));
    }

    @Test
    @DisplayName("빈 문항이나 빈 보기가 섞여 있으면 500 이 아니라 해당 칸 검증 오류가 난다")
    void givenNullQuestionOrOption_whenCreate_thenFieldErrors() {
        java.util.List<TrackQuestion> questions = new java.util.ArrayList<>();
        questions.add(null);
        java.util.List<String> options = new java.util.ArrayList<>(List.of("A"));
        options.add(null);
        questions.add(new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE, true, options));

        assertThatThrownBy(() -> RecruitTrack.create("seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT,
                20, 29, questions, 0))
                .isInstanceOf(InvalidRecruitTrackException.class)
                .satisfies(e -> assertThat(((InvalidRecruitTrackException) e).getFieldErrors()).extracting("field")
                        .containsExactly("questions[0]", "questions[1].options"));
        assertThatThrownBy(() -> seoulStudent().revise(null, null, null, null, null, questions, null, null))
                .isInstanceOf(InvalidRecruitTrackException.class);
    }

    @Test
    @DisplayName("수정은 보낸 칸만 바꾸고 키는 그대로 둔다")
    void givenPartialUpdate_whenRevise_thenKeepsOthers() {
        RecruitTrack revised = seoulStudent().revise("서울 대학생(신촌)", null, null, null, 30, List.of(MBTI), 3, false);

        assertThat(revised.key()).isEqualTo("seoul-student");
        assertThat(revised.label()).isEqualTo("서울 대학생(신촌)");
        assertThat(revised.region()).isEqualTo("서울");
        assertThat(revised.minAge()).isEqualTo(20);
        assertThat(revised.maxAge()).isEqualTo(30);
        assertThat(revised.questions()).containsExactly(MBTI);
        assertThat(revised.displayOrder()).isEqualTo(3);
        assertThat(revised.active()).isFalse();
    }

    @Test
    @DisplayName("문항은 저장 형태(JSON 객체)로 바꿨다가 그대로 되돌릴 수 있다")
    void givenQuestion_whenStoredAndRestored_thenSame() {
        Map<String, Object> stored = GRADE.toStored();

        assertThat(stored).containsEntry("key", "grade").containsEntry("type", "CHOICE").containsEntry("required", true);
        assertThat(TrackQuestion.fromStored(stored)).isEqualTo(GRADE);
        assertThat(TrackQuestion.fromStored(MBTI.toStored())).isEqualTo(MBTI);
    }
}
