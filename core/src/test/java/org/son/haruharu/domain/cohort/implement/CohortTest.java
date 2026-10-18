package org.son.haruharu.domain.cohort.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.cohort.implement.exception.CohortNotCancellableException;
import org.son.haruharu.domain.cohort.implement.exception.CohortNotEditableException;
import org.son.haruharu.domain.cohort.implement.exception.CohortNotPublishableException;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CohortTest {

    private static final LocalDateTime OPENS = LocalDateTime.of(2026, 10, 20, 10, 0);
    private static final LocalDateTime CLOSES = LocalDateTime.of(2026, 10, 25, 23, 59);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 10, 27, 12, 0);

    private Cohort draft() {
        return Cohort.create(1L, 6, OPENS, CLOSES, DEADLINE, "10월 마지막 주", 6, 6, "admin:1");
    }

    @Test
    @DisplayName("새 기수는 초안(DRAFT)으로 만들어지고 신청을 받지 않는다")
    void givenNewCohort_whenCreated_thenDraftAndNotAccepting() {
        Cohort cohort = draft();

        assertThat(cohort.status()).isEqualTo(CohortStatus.DRAFT);
        assertThat(cohort.isAcceptingApplications(OPENS.plusHours(1))).isFalse();
    }

    @Test
    @DisplayName("공개된 기수는 apply_opens_at 이상 apply_closes_at 미만에서만 신청을 받는다")
    void givenOpenCohort_whenCheckingWindow_thenOpensInclusiveClosesExclusive() {
        Cohort open = draft().publish(OPENS.minusDays(1));

        assertThat(open.isAcceptingApplications(OPENS.minusNanos(1))).isFalse();
        assertThat(open.isAcceptingApplications(OPENS)).isTrue();
        assertThat(open.isAcceptingApplications(CLOSES.minusNanos(1))).isTrue();
        assertThat(open.isAcceptingApplications(CLOSES)).isFalse();
    }

    @Test
    @DisplayName("취소된 기수는 기간 안이어도 신청을 받지 않는다")
    void givenCancelledCohort_whenInWindow_thenNotAccepting() {
        Cohort cancelled = draft().publish(OPENS.minusDays(1)).cancel();

        assertThat(cancelled.status()).isEqualTo(CohortStatus.CANCELLED);
        assertThat(cancelled.isAcceptingApplications(OPENS.plusHours(1))).isFalse();
    }

    @Test
    @DisplayName("신청 시작이 마감보다 늦거나 같으면 칸별 검증 오류가 난다")
    void givenOpensNotBeforeCloses_whenCreate_thenFieldError() {
        assertThatThrownBy(() -> Cohort.create(1L, 6, CLOSES, CLOSES, DEADLINE, "10월 마지막 주", null, null, "admin:1"))
                .isInstanceOf(InvalidCohortException.class)
                .satisfies(e -> assertThat(((InvalidCohortException) e).getFieldErrors())
                        .extracting("field").containsExactly("apply_closes_at"));
    }

    @Test
    @DisplayName("배정 마감이 신청 마감보다 이르면 칸별 검증 오류가 난다")
    void givenDeadlineBeforeCloses_whenCreate_thenFieldError() {
        assertThatThrownBy(() -> Cohort.create(1L, 6, OPENS, CLOSES, CLOSES.minusMinutes(1), "10월 마지막 주", null, null, "admin:1"))
                .isInstanceOf(InvalidCohortException.class)
                .satisfies(e -> assertThat(((InvalidCohortException) e).getFieldErrors())
                        .extracting("field").containsExactly("assign_deadline_at"));
    }

    @Test
    @DisplayName("기수 번호·기간 이름·목표 인원이 잘못되면 해당 칸을 모두 알려준다")
    void givenSeveralInvalidFields_whenCreate_thenReportsEachField() {
        assertThatThrownBy(() -> Cohort.create(1L, 0, OPENS, CLOSES, DEADLINE, " ", -1, -2, "admin:1"))
                .isInstanceOf(InvalidCohortException.class)
                .satisfies(e -> assertThat(((InvalidCohortException) e).getFieldErrors()).extracting("field")
                        .containsExactlyInAnyOrder("sequence", "period_label", "target_male", "target_female"));
    }

    @Test
    @DisplayName("신청 마감이 지난 기수는 공개할 수 없다")
    void givenClosedWindow_whenPublish_thenFieldError() {
        assertThatThrownBy(() -> draft().publish(CLOSES))
                .isInstanceOf(InvalidCohortException.class);
    }

    @Test
    @DisplayName("초안이 아닌 기수는 공개할 수 없다")
    void givenOpenCohort_whenPublishAgain_thenConflict() {
        Cohort open = draft().publish(OPENS);

        assertThatThrownBy(() -> open.publish(OPENS)).isInstanceOf(CohortNotPublishableException.class);
        assertThatThrownBy(() -> open.cancel().publish(OPENS)).isInstanceOf(CohortNotPublishableException.class);
    }

    @Test
    @DisplayName("취소된 기수는 다시 취소하거나 수정할 수 없다")
    void givenCancelledCohort_whenCancelOrRevise_thenConflict() {
        Cohort cancelled = draft().cancel();

        assertThatThrownBy(cancelled::cancel).isInstanceOf(CohortNotCancellableException.class);
        assertThatThrownBy(() -> cancelled.revise(null, null, null, null, "11월 첫째 주", null, null))
                .isInstanceOf(CohortNotEditableException.class);
    }

    @Test
    @DisplayName("수정은 보낸 칸만 바꾸고 순서 규칙을 다시 검사한다")
    void givenPartialRevision_whenRevise_thenKeepsOthersAndValidates() {
        Cohort revised = draft().revise(7, null, CLOSES.plusDays(1), DEADLINE.plusDays(1), null, 8, null);

        assertThat(revised.sequence()).isEqualTo(7);
        assertThat(revised.applyOpensAt()).isEqualTo(OPENS);
        assertThat(revised.applyClosesAt()).isEqualTo(CLOSES.plusDays(1));
        assertThat(revised.targetMale()).isEqualTo(8);
        assertThat(revised.targetFemale()).isEqualTo(6);
        assertThat(revised.periodLabel()).isEqualTo("10월 마지막 주");

        assertThatThrownBy(() -> draft().revise(null, null, CLOSES.plusDays(5), null, null, null, null))
                .isInstanceOf(InvalidCohortException.class);
    }

    @Test
    @DisplayName("공개된 기수는 일정은 고칠 수 있지만 기수 번호는 바꿀 수 없다")
    void givenOpenCohort_whenReviseSequence_thenFieldError() {
        Cohort open = draft().publish(OPENS);

        assertThat(open.revise(null, null, CLOSES.plusHours(1), null, null, null, null).applyClosesAt())
                .isEqualTo(CLOSES.plusHours(1));
        assertThatThrownBy(() -> open.revise(7, null, null, null, null, null, null))
                .isInstanceOf(InvalidCohortException.class);
        // 같은 번호를 다시 보내는 것은 변경이 아니다.
        assertThat(open.revise(6, null, null, null, null, null, null).sequence()).isEqualTo(6);
    }

    @Test
    @DisplayName("기수 이름은 트랙 이름 뒤에 번호를 붙인다")
    void givenTrackLabel_whenDisplayName_thenLabelAndSequence() {
        assertThat(draft().displayName("서울 대학생")).isEqualTo("서울 대학생 6기");
    }
}
