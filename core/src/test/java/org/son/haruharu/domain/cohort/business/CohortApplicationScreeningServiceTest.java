package org.son.haruharu.domain.cohort.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.ApplicationReviewReason;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CohortApplicationScreeningServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final Long USER = 7L;
    private static final LocalDate TWENTY_THREE = LocalDate.of(2003, 5, 1);

    @Mock CohortApplicationReader applicationReader;
    @Mock CohortApplicationWriter applicationWriter;
    @Mock RecruitTrackReader trackReader;
    @Mock IdentityService identityService;
    CohortApplicationScreeningService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new CohortApplicationScreeningService(applicationReader, applicationWriter, trackReader,
                identityService, clock);
    }

    private static RecruitTrack track() {
        return new RecruitTrack(1L, "seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT, 20, 29, List.of(), 1,
                true, NOW, NOW);
    }

    private static CohortApplication application(CohortApplicationStatus status, ApplicantGender declared) {
        return new CohortApplication(50L, USER, 3L, 1L, status, declared, null, null, "한국대", "컴퓨터공학과", Map.of(),
                null, null, null, null, 0, NOW.minusHours(1), null, null, null, null, null, null, null, null, null,
                NOW, NOW);
    }

    private void givenPending(ApplicantGender declared) {
        given(applicationReader.findLatestByUserId(USER))
                .willReturn(Optional.of(application(CohortApplicationStatus.IDENTITY_PENDING, declared)));
    }

    private void givenPass(String gender, LocalDate birthday) {
        given(identityService.findVerified(USER)).willReturn(Optional.of(new VerifiedIdentity(gender, birthday)));
    }

    private CohortApplication screened() {
        ArgumentCaptor<CohortApplication> captor = ArgumentCaptor.forClass(CohortApplication.class);
        verify(applicationWriter).screen(captor.capture(), any());
        return captor.getValue();
    }

    @Test
    @DisplayName("PASS 성별·나이가 맞으면 PASS 대기 신청을 편성 대기로 넘기고 PASS 성별·생년을 스냅샷한다")
    void givenMatchingPass_whenScreen_thenQueuedWithSnapshot() {
        givenPending(ApplicantGender.MALE);
        givenPass("MALE", TWENTY_THREE);
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationWriter.screen(any(), any())).willReturn(true);

        assertThat(service.screenPending(USER)).isTrue();

        CohortApplication screened = screened();
        assertThat(screened.id()).isEqualTo(50L);
        assertThat(screened.status()).isEqualTo(CohortApplicationStatus.QUEUED);
        assertThat(screened.queuedAt()).isEqualTo(NOW);
        assertThat(screened.passGender()).isEqualTo("MALE");
        assertThat(screened.birthday()).isEqualTo(TWENTY_THREE);
        assertThat(screened.reviewReason()).isNull();
    }

    @Test
    @DisplayName("PASS 성별이 신청 성별과 다르면 확인 필요(GENDER_MISMATCH)")
    void givenGenderMismatch_whenScreen_thenNeedsReview() {
        givenPending(ApplicantGender.FEMALE);
        givenPass("MALE", TWENTY_THREE);
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationWriter.screen(any(), any())).willReturn(true);

        service.screenPending(USER);

        assertThat(screened().status()).isEqualTo(CohortApplicationStatus.NEEDS_REVIEW);
        assertThat(screened().reviewReason()).isEqualTo(ApplicationReviewReason.GENDER_MISMATCH);
        assertThat(screened().queuedAt()).isNull();
    }

    @Test
    @DisplayName("PASS 성별이 OTHER 처럼 해석할 수 없는 값이면 확인 필요(UNKNOWN_GENDER)")
    void givenOtherGender_whenScreen_thenUnknownGender() {
        givenPending(ApplicantGender.MALE);
        givenPass("OTHER", TWENTY_THREE);
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationWriter.screen(any(), any())).willReturn(true);

        service.screenPending(USER);

        assertThat(screened().reviewReason()).isEqualTo(ApplicationReviewReason.UNKNOWN_GENDER);
        assertThat(screened().passGender()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("연 나이가 트랙 범위 밖이면 확인 필요(AGE_OUT_OF_RANGE)")
    void givenAgeOutOfRange_whenScreen_thenAgeOut() {
        givenPending(ApplicantGender.MALE);
        givenPass("MALE", LocalDate.of(1990, 1, 1));
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationWriter.screen(any(), any())).willReturn(true);

        service.screenPending(USER);

        assertThat(screened().reviewReason()).isEqualTo(ApplicationReviewReason.AGE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("프로필 성별이 없어 신청 성별이 비어 있으면 PASS 성별을 신청 성별로 채운다")
    void givenNoDeclaredGender_whenScreen_thenDeclaredFromPass() {
        givenPending(null);
        givenPass("FEMALE", TWENTY_THREE);
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationWriter.screen(any(), any())).willReturn(true);

        service.screenPending(USER);

        assertThat(screened().declaredGender()).isEqualTo(ApplicantGender.FEMALE);
        assertThat(screened().status()).isEqualTo(CohortApplicationStatus.QUEUED);
    }

    @Test
    @DisplayName("PASS 를 아직 안 했으면 아무것도 바꾸지 않는다")
    void givenNotVerified_whenScreen_thenNoChange() {
        givenPending(ApplicantGender.MALE);
        given(identityService.findVerified(USER)).willReturn(Optional.empty());

        assertThat(service.screenPending(USER)).isFalse();
        verifyNoInteractions(applicationWriter);
    }

    @Test
    @DisplayName("이미 판정된 신청·닫힌 신청·신청 없음은 다시 판정하지 않는다(재실행해도 안전)")
    void givenNotPending_whenScreen_thenNoop() {
        given(applicationReader.findLatestByUserId(USER))
                .willReturn(Optional.of(application(CohortApplicationStatus.QUEUED, ApplicantGender.MALE)))
                .willReturn(Optional.of(application(CohortApplicationStatus.CANCELLED, ApplicantGender.MALE)))
                .willReturn(Optional.empty());

        assertThat(service.screenPending(USER)).isFalse();
        assertThat(service.screenPending(USER)).isFalse();
        assertThat(service.screenPending(USER)).isFalse();
        verifyNoInteractions(identityService, applicationWriter);
    }

    @Test
    @DisplayName("그 사이 다른 경로가 먼저 판정·취소했으면(조건부 전이 0건) false")
    void givenLostRace_whenScreen_thenFalse() {
        givenPending(ApplicantGender.MALE);
        givenPass("MALE", TWENTY_THREE);
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationWriter.screen(any(), any())).willReturn(false);

        assertThat(service.screenPending(USER)).isFalse();
    }

    @Test
    @DisplayName("커밋 후 경로도 같은 판정을 한다")
    void givenPending_whenScreenAfterIdentityVerified_thenScreens() {
        givenPending(ApplicantGender.MALE);
        givenPass("MALE", TWENTY_THREE);
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationWriter.screen(any(), any())).willReturn(true);

        assertThat(service.screenAfterIdentityVerified(USER)).isTrue();
        assertThat(screened().status()).isEqualTo(CohortApplicationStatus.QUEUED);
    }
}
