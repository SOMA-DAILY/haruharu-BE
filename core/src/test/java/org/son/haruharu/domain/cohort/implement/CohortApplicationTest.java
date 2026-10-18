package org.son.haruharu.domain.cohort.implement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CohortApplicationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final RecruitTrack TRACK = new RecruitTrack(1L, "seoul-student", "서울 대학생", "서울",
            RecruitAudience.STUDENT, 20, 29, List.of(), 1, true, NOW, NOW);
    private static final Cohort COHORT = new Cohort(3L, 1L, 6, CohortStatus.OPEN, NOW.minusDays(1), NOW.plusDays(4),
            NOW.plusDays(5), "10월 마지막 주", 6, 6, null, "admin:1", NOW, NOW);
    private static final ApplicationForm FORM = new ApplicationForm("한국대", "컴퓨터공학과", "01012345678",
            Map.of("grade", "3학년"));

    private CohortApplication pending(ApplicantGender declared) {
        return CohortApplication.create(7L, COHORT, FORM, declared, null, NOW);
    }

    private CohortApplication previous(CohortApplicationStatus status) {
        return new CohortApplication(1L, 7L, 2L, 1L, status, null, null, null, "한국대", "컴퓨터공학과", null, null,
                null, null, null, 0, NOW, null, null, null, null, null, NOW, null, null, null, NOW, NOW);
    }

    @Test
    @DisplayName("새 신청은 PASS 대기로 시작하고 기수·트랙·신청서를 옮겨 적는다")
    void givenForm_whenCreate_thenIdentityPending() {
        CohortApplication application = pending(ApplicantGender.MALE);

        assertThat(application.status()).isEqualTo(CohortApplicationStatus.IDENTITY_PENDING);
        assertThat(application.cohortId()).isEqualTo(3L);
        assertThat(application.trackId()).isEqualTo(1L);
        assertThat(application.answers()).containsEntry("grade", "3학년");
        assertThat(application.appliedAt()).isEqualTo(NOW);
        assertThat(application.queuedAt()).isNull();
        assertThat(application.priority()).isZero();
        assertThat(application.cancellableByApplicant()).isTrue();
    }

    @Test
    @DisplayName("직전 신청이 미배정으로 닫혔으면 우선순위 1, 본인 취소·완료였으면 0")
    void givenPreviousApplication_whenCreate_thenPriorityFollowsNotAssigned() {
        assertThat(CohortApplication.create(7L, COHORT, FORM, null,
                previous(CohortApplicationStatus.NOT_ASSIGNED), NOW).priority()).isEqualTo(1);
        assertThat(CohortApplication.create(7L, COHORT, FORM, null,
                previous(CohortApplicationStatus.CANCELLED), NOW).priority()).isZero();
        assertThat(CohortApplication.create(7L, COHORT, FORM, null,
                previous(CohortApplicationStatus.COMPLETED), NOW).priority()).isZero();
    }

    @Test
    @DisplayName("PASS 성별이 신청 성별과 같고 연 나이가 범위 안이면 편성 대기가 되고 대기 시각을 적는다")
    void givenMatchingPass_whenScreen_thenQueued() {
        CohortApplication screened = pending(ApplicantGender.MALE)
                .screen("MALE", LocalDate.of(2003, 5, 1), TRACK, NOW);

        assertThat(screened.status()).isEqualTo(CohortApplicationStatus.QUEUED);
        assertThat(screened.queuedAt()).isEqualTo(NOW);
        assertThat(screened.reviewReason()).isNull();
        assertThat(screened.passGender()).isEqualTo("MALE");
        assertThat(screened.birthday()).isEqualTo(LocalDate.of(2003, 5, 1));
    }

    @Test
    @DisplayName("신청 성별이 비어 있으면 PASS 성별을 신청 성별로 쓰고 편성 대기가 된다")
    void givenNoDeclaredGender_whenScreen_thenUsesPassGender() {
        CohortApplication screened = pending(null).screen("FEMALE", LocalDate.of(2003, 5, 1), TRACK, NOW);

        assertThat(screened.declaredGender()).isEqualTo(ApplicantGender.FEMALE);
        assertThat(screened.status()).isEqualTo(CohortApplicationStatus.QUEUED);
    }

    @Test
    @DisplayName("성별 불일치·해석 불가·나이 범위 밖이면 확인 필요가 되고 대기 시각은 비운다")
    void givenMismatch_whenScreen_thenNeedsReview() {
        LocalDate twentyFour = LocalDate.of(2002, 1, 1);
        assertThat(pending(ApplicantGender.FEMALE).screen("MALE", twentyFour, TRACK, NOW))
                .satisfies(a -> {
                    assertThat(a.status()).isEqualTo(CohortApplicationStatus.NEEDS_REVIEW);
                    assertThat(a.reviewReason()).isEqualTo(ApplicationReviewReason.GENDER_MISMATCH);
                    assertThat(a.queuedAt()).isNull();
                });
        assertThat(pending(ApplicantGender.MALE).screen("OTHER", twentyFour, TRACK, NOW).reviewReason())
                .isEqualTo(ApplicationReviewReason.UNKNOWN_GENDER);
        assertThat(pending(ApplicantGender.MALE).screen(null, twentyFour, TRACK, NOW).reviewReason())
                .isEqualTo(ApplicationReviewReason.UNKNOWN_GENDER);
        assertThat(pending(ApplicantGender.MALE).screen("MALE", LocalDate.of(1990, 1, 1), TRACK, NOW).reviewReason())
                .isEqualTo(ApplicationReviewReason.AGE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("나이는 연 나이(올해 − 출생연도)로 보고 범위 양 끝을 포함한다: 생일이 지났는지는 보지 않는다")
    void givenBoundaryBirthYears_whenScreen_thenInclusiveYearAge() {
        // NOW = 2026-10-21, 트랙 20~29세. 1997년생 = 29세(통과), 1996년생 = 30세(확인 필요).
        assertThat(pending(ApplicantGender.MALE).screen("MALE", LocalDate.of(1997, 1, 1), TRACK, NOW).status())
                .isEqualTo(CohortApplicationStatus.QUEUED);
        assertThat(pending(ApplicantGender.MALE).screen("MALE", LocalDate.of(1996, 12, 31), TRACK, NOW).reviewReason())
                .isEqualTo(ApplicationReviewReason.AGE_OUT_OF_RANGE);
        // 2006-12-31 생은 아직 만 19세지만 연 나이 20세라 통과, 2007년생은 19세라 확인 필요.
        assertThat(pending(ApplicantGender.MALE).screen("MALE", LocalDate.of(2006, 12, 31), TRACK, NOW).status())
                .isEqualTo(CohortApplicationStatus.QUEUED);
        assertThat(pending(ApplicantGender.MALE).screen("MALE", LocalDate.of(2007, 1, 1), TRACK, NOW).reviewReason())
                .isEqualTo(ApplicationReviewReason.AGE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("이미 판정된 신청은 다시 판정하지 않는다")
    void givenScreened_whenScreenAgain_thenThrows() {
        CohortApplication queued = pending(ApplicantGender.MALE).screen("MALE", LocalDate.of(2003, 1, 1), TRACK, NOW);

        assertThatThrownBy(() -> queued.screen("MALE", LocalDate.of(2003, 1, 1), TRACK, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("본인 취소는 배정 전 상태에서만 열린다")
    void givenStatuses_whenCancellable_thenOnlyBeforeAssignment() {
        assertThat(previous(CohortApplicationStatus.IDENTITY_PENDING).cancellableByApplicant()).isTrue();
        assertThat(previous(CohortApplicationStatus.NEEDS_REVIEW).cancellableByApplicant()).isTrue();
        assertThat(previous(CohortApplicationStatus.QUEUED).cancellableByApplicant()).isTrue();
        assertThat(previous(CohortApplicationStatus.ASSIGNED).cancellableByApplicant()).isFalse();
        assertThat(previous(CohortApplicationStatus.CANCELLED).cancellableByApplicant()).isFalse();
        assertThat(previous(CohortApplicationStatus.NOT_ASSIGNED).cancellableByApplicant()).isFalse();
    }
}
