package org.son.haruharu.domain.cohort.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.cohort.business.command.ApplyCohortCommand;
import org.son.haruharu.domain.cohort.business.command.SubmitApplicationSurveyCommand;
import org.son.haruharu.domain.cohort.business.dto.CohortApplicationResponse;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationClosedException;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationInProgressException;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationNotCancellableException;
import org.son.haruharu.domain.cohort.business.exception.CohortNotAcceptingApplicationsException;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.FriendPreference;
import org.son.haruharu.domain.cohort.implement.RecruitAudience;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.cohort.implement.TrackQuestion;
import org.son.haruharu.domain.cohort.implement.TrackQuestionType;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortApplicationException;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketSpendCommand;
import org.son.haruharu.domain.ticket.implement.exception.InsufficientTicketException;
import org.son.haruharu.domain.cohort.storage.exception.CohortApplicationNotFoundException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CohortApplicationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final Long USER = 7L;

    @Mock CohortReader cohortReader;
    @Mock RecruitTrackReader trackReader;
    @Mock CohortApplicationReader applicationReader;
    @Mock CohortApplicationWriter applicationWriter;
    @Mock CohortApplicationClosingService closingService;
    @Mock TicketService ticketService;
    @Mock IdentityService identityService;
    @Mock UserProfileReader userProfileReader;
    @Mock ProfileService profileService;
    CohortApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new CohortApplicationService(cohortReader, trackReader, applicationReader, applicationWriter,
                closingService, ticketService, identityService, userProfileReader, profileService, clock);
    }

    private static RecruitTrack track() {
        return new RecruitTrack(1L, "seoul-student", "서울 대학생", "서울", RecruitAudience.STUDENT, 20, 29,
                List.of(new TrackQuestion("grade", "학년", TrackQuestionType.CHOICE, true, List.of("1학년", "3학년"))),
                1, true, NOW, NOW);
    }

    private static Cohort cohort(CohortStatus status, LocalDateTime opensAt, LocalDateTime closesAt) {
        return new Cohort(3L, 1L, 6, status, opensAt, closesAt, closesAt.plusDays(1), "10월 마지막 주", 6, 6, null,
                "admin:1", NOW, NOW);
    }

    private static Cohort openCohort() {
        return cohort(CohortStatus.OPEN, NOW.minusDays(1), NOW.plusDays(3));
    }

    private static ApplyCohortCommand command() {
        return new ApplyCohortCommand("한국대", "컴퓨터공학과", "010-1234-5678", Map.of("grade", "3학년"));
    }

    private static CohortApplication application(Long id, CohortApplicationStatus status) {
        return new CohortApplication(id, USER, 3L, 1L, status, ApplicantGender.MALE, null, null, "한국대",
                "컴퓨터공학과", Map.of(), null, null, null, null, 0, NOW, null, null, null, null, null, null, null,
                null, null, NOW, NOW);
    }

    private static UserProfile profile(Gender gender) {
        return new UserProfile(11L, USER, "서울", "직장인", 175, "INFP", "테스터", null, null, gender);
    }

    private void givenOpenCohortAndTrack() {
        given(cohortReader.getByIdForShare(3L)).willReturn(openCohort());
        given(trackReader.getById(1L)).willReturn(track());
    }

    private void givenInsertReturnsId(long id) {
        given(applicationWriter.insert(any())).willAnswer(invocation -> {
            CohortApplication a = invocation.getArgument(0);
            return new CohortApplication(id, a.userId(), a.cohortId(), a.trackId(), a.status(), a.declaredGender(),
                    a.passGender(), a.birthday(), a.school(), a.department(), a.answers(), null, null, null, null,
                    a.priority(), a.appliedAt(), a.queuedAt(), a.reviewReason(), null, null, null, null, null, null,
                    null, NOW, NOW);
        });
    }

    @Test
    @DisplayName("신청은 기수 공유 잠금 → 지갑 잠금 → 진행 중 확인 → INSERT → 참가권 사용 순서로 하고 프로필에 연락처를 남긴다")
    void givenOpenCohort_whenApply_thenLocksInOrderAndSpendsOneTicket() {
        givenOpenCohortAndTrack();
        given(applicationReader.existsInProgress(USER)).willReturn(false);
        given(applicationReader.findLatestByUserId(USER)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.of(profile(Gender.MALE)));
        given(identityService.findVerified(USER)).willReturn(Optional.empty());
        givenInsertReturnsId(100L);

        CohortApplicationResponse response = service.apply(USER, 3L, command());

        assertThat(response.status()).isEqualTo(CohortApplicationStatus.IDENTITY_PENDING);
        assertThat(response.cohortName()).isEqualTo("서울 대학생 6기");
        assertThat(response.isCancellable()).isTrue();
        InOrder order = inOrder(cohortReader, ticketService, applicationReader, applicationWriter);
        order.verify(cohortReader).getByIdForShare(3L);
        order.verify(ticketService).lockWallet(USER);
        order.verify(applicationReader).existsInProgress(USER);
        order.verify(applicationWriter).insert(any());
        order.verify(ticketService).spend(new TicketSpendCommand(USER, 1, "COHORT_APPLICATION", "100"));
        verify(profileService).saveApplicantContact(USER, "한국대", "컴퓨터공학과", "01012345678");
    }

    @Test
    @DisplayName("PASS 를 이미 했으면 신청 즉시 판정해 편성 대기로 저장한다")
    void givenVerifiedUser_whenApply_thenQueuedImmediately() {
        givenOpenCohortAndTrack();
        given(applicationReader.findLatestByUserId(USER)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.of(profile(Gender.FEMALE)));
        given(identityService.findVerified(USER))
                .willReturn(Optional.of(new VerifiedIdentity("FEMALE", LocalDate.of(2003, 3, 1))));
        givenInsertReturnsId(100L);

        service.apply(USER, 3L, command());

        ArgumentCaptor<CohortApplication> inserted = ArgumentCaptor.forClass(CohortApplication.class);
        verify(applicationWriter).insert(inserted.capture());
        assertThat(inserted.getValue().status()).isEqualTo(CohortApplicationStatus.QUEUED);
        assertThat(inserted.getValue().declaredGender()).isEqualTo(ApplicantGender.FEMALE);
        assertThat(inserted.getValue().queuedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("프로필이 없으면 신청 성별을 비워 두고 PASS 판정 때 채운다")
    void givenNoProfile_whenApply_thenDeclaredGenderEmpty() {
        givenOpenCohortAndTrack();
        given(applicationReader.findLatestByUserId(USER)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.empty());
        given(identityService.findVerified(USER)).willReturn(Optional.empty());
        givenInsertReturnsId(100L);

        service.apply(USER, 3L, command());

        ArgumentCaptor<CohortApplication> inserted = ArgumentCaptor.forClass(CohortApplication.class);
        verify(applicationWriter).insert(inserted.capture());
        assertThat(inserted.getValue().declaredGender()).isNull();
    }

    @Test
    @DisplayName("직전 신청이 미배정으로 닫혔으면 우선순위 1 로 신청한다")
    void givenPreviousNotAssigned_whenApply_thenPrioritized() {
        givenOpenCohortAndTrack();
        given(applicationReader.findLatestByUserId(USER))
                .willReturn(Optional.of(application(50L, CohortApplicationStatus.NOT_ASSIGNED)));
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.empty());
        given(identityService.findVerified(USER)).willReturn(Optional.empty());
        givenInsertReturnsId(100L);

        service.apply(USER, 3L, command());

        ArgumentCaptor<CohortApplication> inserted = ArgumentCaptor.forClass(CohortApplication.class);
        verify(applicationWriter).insert(inserted.capture());
        assertThat(inserted.getValue().priority()).isEqualTo(1);
    }

    @Test
    @DisplayName("신청 기간 밖·공개 전 기수는 지갑을 잠그기 전에 409 로 거절한다")
    void givenNotAccepting_whenApply_thenRejectsBeforeWallet() {
        given(cohortReader.getByIdForShare(3L))
                .willReturn(cohort(CohortStatus.OPEN, NOW.minusDays(5), NOW.minusSeconds(1)))
                .willReturn(cohort(CohortStatus.OPEN, NOW.plusSeconds(1), NOW.plusDays(3)))
                .willReturn(cohort(CohortStatus.DRAFT, NOW.minusDays(1), NOW.plusDays(3)));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.apply(USER, 3L, command()))
                    .isInstanceOf(CohortNotAcceptingApplicationsException.class);
        }
        verifyNoInteractions(ticketService, applicationWriter);
    }

    @Test
    @DisplayName("신청 시작 시각 정각은 신청할 수 있다")
    void givenExactlyOpensAt_whenApply_thenAccepted() {
        given(cohortReader.getByIdForShare(3L)).willReturn(cohort(CohortStatus.OPEN, NOW, NOW.plusDays(3)));
        given(trackReader.getById(1L)).willReturn(track());
        given(applicationReader.findLatestByUserId(USER)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.empty());
        given(identityService.findVerified(USER)).willReturn(Optional.empty());
        givenInsertReturnsId(100L);

        assertThat(service.apply(USER, 3L, command()).applicationId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("칸 오류는 지갑을 잠그기 전에 400 VALIDATION_ERROR 로 거절한다")
    void givenInvalidForm_whenApply_thenRejectsBeforeWallet() {
        givenOpenCohortAndTrack();

        assertThatThrownBy(() -> service.apply(USER, 3L,
                new ApplyCohortCommand("한국대", "컴퓨터공학과", "1234", Map.of("grade", "3학년"))))
                .isInstanceOf(InvalidCohortApplicationException.class);
        verifyNoInteractions(ticketService, applicationWriter);
    }

    @Test
    @DisplayName("진행 중인 신청이 있으면 409 로 거절하고 참가권을 쓰지 않는다")
    void givenInProgress_whenApply_thenRejects() {
        givenOpenCohortAndTrack();
        given(applicationReader.existsInProgress(USER)).willReturn(true);

        assertThatThrownBy(() -> service.apply(USER, 3L, command()))
                .isInstanceOf(CohortApplicationInProgressException.class);
        verify(applicationWriter, never()).insert(any());
        verify(ticketService, never()).spend(any());
    }

    @Test
    @DisplayName("참가권이 모자라면 예외가 그대로 나가 신청 INSERT 까지 함께 롤백된다(프로필도 저장하지 않는다)")
    void givenNoTicket_whenApply_thenPropagatesInsufficient() {
        givenOpenCohortAndTrack();
        given(applicationReader.findLatestByUserId(USER)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.empty());
        given(identityService.findVerified(USER)).willReturn(Optional.empty());
        givenInsertReturnsId(100L);
        willThrow(new InsufficientTicketException()).given(ticketService).spend(any());

        assertThatThrownBy(() -> service.apply(USER, 3L, command())).isInstanceOf(InsufficientTicketException.class);
        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("신청서 기본값: 학교·학과는 직전 신청서를 먼저 쓰고, 번호는 프로필에서 가져온다")
    void givenPreviousApplication_whenGetMine_thenDefaultsFromApplication() {
        given(applicationReader.findLatestByUserId(USER))
                .willReturn(Optional.of(application(50L, CohortApplicationStatus.CANCELLED)));
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.of(new UserProfile(11L, USER, "서울",
                "개발자", 175, "INFP", "테스터", null, null, null, 2003, "전자공학과", "러닝", "01012345678")));
        given(cohortReader.getById(3L)).willReturn(openCohort());
        given(trackReader.getById(1L)).willReturn(track());

        var mine = service.getMine(USER);

        assertThat(mine.application().applicationId()).isEqualTo(50L);
        assertThat(mine.defaults().school()).isEqualTo("한국대");
        assertThat(mine.defaults().department()).isEqualTo("컴퓨터공학과");
        assertThat(mine.defaults().phone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("신청한 적이 없으면 학교·학과를 프로필에서 가져오고, 프로필도 없으면 모두 null")
    void givenNoApplication_whenGetMine_thenDefaultsFromProfile() {
        given(applicationReader.findLatestByUserId(USER)).willReturn(Optional.empty());
        given(userProfileReader.findByUserId(USER)).willReturn(Optional.of(new UserProfile(11L, USER, null,
                "한국대", null, null, "테스터", null, null, null, null, "컴퓨터공학과", null, null)));

        var mine = service.getMine(USER);

        assertThat(mine.application()).isNull();
        assertThat(mine.defaults().school()).isEqualTo("한국대");
        assertThat(mine.defaults().department()).isEqualTo("컴퓨터공학과");
        assertThat(mine.defaults().phone()).isNull();

        given(userProfileReader.findByUserId(USER)).willReturn(Optional.empty());
        assertThat(service.getMine(USER).defaults()).isEqualTo(
                new org.son.haruharu.domain.cohort.business.dto.ApplicationDefaultsResponse(null, null, null));
    }

    @Test
    @DisplayName("배정 전 신청을 취소하면 CANCELLED(USER_CANCELLED)로 닫고 반환을 맡긴다")
    void givenQueued_whenCancel_thenClosesWithRefund() {
        CohortApplication queued = application(100L, CohortApplicationStatus.QUEUED);
        given(applicationReader.getOwned(100L, USER)).willReturn(queued);
        given(closingService.closeBeforeAssignment(queued, CohortApplicationStatus.CANCELLED,
                ApplicationCloseReason.USER_CANCELLED, NOW)).willReturn(true);
        given(applicationReader.getById(100L)).willReturn(application(100L, CohortApplicationStatus.CANCELLED));
        given(cohortReader.getById(3L)).willReturn(openCohort());
        given(trackReader.getById(1L)).willReturn(track());

        assertThat(service.cancel(USER, 100L).status()).isEqualTo(CohortApplicationStatus.CANCELLED);
    }

    @Test
    @DisplayName("배정된 신청은 앱에서 취소하지 않는다")
    void givenAssigned_whenCancel_thenNotCancellable() {
        given(applicationReader.getOwned(100L, USER)).willReturn(application(100L, CohortApplicationStatus.ASSIGNED));

        assertThatThrownBy(() -> service.cancel(USER, 100L))
                .isInstanceOf(CohortApplicationNotCancellableException.class);
        verifyNoInteractions(closingService);
    }

    @Test
    @DisplayName("동시에 다른 경로가 먼저 닫았으면(조건부 전이 실패) 409 로 알려준다")
    void givenLostRace_whenCancel_thenNotCancellable() {
        CohortApplication queued = application(100L, CohortApplicationStatus.QUEUED);
        given(applicationReader.getOwned(100L, USER)).willReturn(queued);
        given(closingService.closeBeforeAssignment(any(), any(), any(), any())).willReturn(false);

        assertThatThrownBy(() -> service.cancel(USER, 100L))
                .isInstanceOf(CohortApplicationNotCancellableException.class);
    }

    @Test
    @DisplayName("남의 신청은 404 로 막는다")
    void givenOthersApplication_whenCancel_thenNotFound() {
        given(applicationReader.getOwned(100L, USER)).willThrow(new CohortApplicationNotFoundException());

        assertThatThrownBy(() -> service.cancel(USER, 100L)).isInstanceOf(CohortApplicationNotFoundException.class);
    }

    @Test
    @DisplayName("설문은 진행 중인 신청에만 저장하고, 끝난 신청이면 409")
    void givenClosed_whenSubmitSurvey_thenClosed() {
        given(applicationReader.getOwned(100L, USER)).willReturn(application(100L, CohortApplicationStatus.CANCELLED));

        assertThatThrownBy(() -> service.submitSurvey(USER, 100L,
                new SubmitApplicationSurveyCommand("인스타그램", FriendPreference.ALONE, null)))
                .isInstanceOf(CohortApplicationClosedException.class);
        verify(applicationWriter, never()).updateSurvey(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("설문 저장 중 신청이 닫히면(조건부 UPDATE 0건) 409")
    void givenClosedMeanwhile_whenSubmitSurvey_thenClosed() {
        given(applicationReader.getOwned(100L, USER)).willReturn(application(100L, CohortApplicationStatus.QUEUED));
        given(applicationWriter.updateSurvey(anyLong(), anyLong(), any(), any())).willReturn(false);

        assertThatThrownBy(() -> service.submitSurvey(USER, 100L,
                new SubmitApplicationSurveyCommand("인스타그램", FriendPreference.ALONE, null)))
                .isInstanceOf(CohortApplicationClosedException.class);
    }
}
