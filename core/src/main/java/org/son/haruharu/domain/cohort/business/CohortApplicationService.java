package org.son.haruharu.domain.cohort.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.cohort.business.command.ApplyCohortCommand;
import org.son.haruharu.domain.cohort.business.command.SubmitApplicationSurveyCommand;
import org.son.haruharu.domain.cohort.business.dto.ApplicationDefaultsResponse;
import org.son.haruharu.domain.cohort.business.dto.CohortApplicationResponse;
import org.son.haruharu.domain.cohort.business.dto.MyCohortApplicationResponse;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationClosedException;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationInProgressException;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationNotCancellableException;
import org.son.haruharu.domain.cohort.business.exception.CohortNotAcceptingApplicationsException;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.ApplicationCloseReason;
import org.son.haruharu.domain.cohort.implement.ApplicationForm;
import org.son.haruharu.domain.cohort.implement.ApplicationSurvey;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.ticket.business.TicketService;
import org.son.haruharu.domain.ticket.business.command.TicketSpendCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 앱 기수 신청(#511): 신청과 참가권 차감, 내 신청 조회, 결제 뒤 설문, 배정 전 취소.
 * 잠금 순서는 기수 → (방) → 신청(id 순) → 지갑이다. 신청은 새 행이라 기존 신청을 잠그지 않는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class CohortApplicationService {

    private final CohortReader cohortReader;
    private final RecruitTrackReader trackReader;
    private final CohortApplicationReader applicationReader;
    private final CohortApplicationWriter applicationWriter;
    private final CohortApplicationClosingService closingService;
    private final CohortApplicationScreeningService screeningService;
    private final TicketService ticketService;
    private final IdentityService identityService;
    private final UserProfileReader userProfileReader;
    private final ProfileService profileService;
    private final Clock clock;

    public CohortApplicationService(CohortReader cohortReader, RecruitTrackReader trackReader,
                                    CohortApplicationReader applicationReader,
                                    CohortApplicationWriter applicationWriter,
                                    CohortApplicationClosingService closingService,
                                    CohortApplicationScreeningService screeningService, TicketService ticketService,
                                    IdentityService identityService, UserProfileReader userProfileReader,
                                    ProfileService profileService, Clock clock) {
        this.cohortReader = cohortReader;
        this.trackReader = trackReader;
        this.applicationReader = applicationReader;
        this.applicationWriter = applicationWriter;
        this.closingService = closingService;
        this.screeningService = screeningService;
        this.ticketService = ticketService;
        this.identityService = identityService;
        this.userProfileReader = userProfileReader;
        this.profileService = profileService;
        this.clock = clock;
    }

    /**
     * 신청하고 같은 트랜잭션에서 참가권 1장을 쓴다. 잔액이 모자라면 신청도 남기지 않는다(400 TICKET_INSUFFICIENT) —
     * 결제 웹훅이 늦게 오면 앱이 다시 시도한다. PASS 를 이미 했으면 바로 편성 대기·확인 필요로 판정한다.
     * <ol>
     *   <li>기수 공유 잠금 → 신청 기간 확인. 기수 취소·정산(배타 잠금)과 겹치지 않는다</li>
     *   <li>지갑 잠금 → 진행 중 신청 확인. 같은 유저의 동시 신청은 여기서 한 줄로 서서 하나만 통과한다</li>
     *   <li>INSERT → 참가권 사용(ref = COHORT_APPLICATION:신청 id) → 프로필에 학교·학과·번호 저장</li>
     * </ol>
     * 진행 중 신청 1건은 DB 부분 유니크(ux_cohort_applications_user_active)가 최종으로 막는다.
     */
    @Transactional
    public CohortApplicationResponse apply(Long userId, Long cohortId, ApplyCohortCommand command) {
        Cohort cohort = cohortReader.getByIdForShare(cohortId);
        LocalDateTime now = now();
        if (!cohort.isAcceptingApplications(now)) throw new CohortNotAcceptingApplicationsException();
        RecruitTrack track = trackReader.getById(cohort.trackId());
        ApplicationForm form = command.toForm().validated(track.questions());

        ticketService.lockWallet(userId);
        if (applicationReader.existsInProgress(userId)) throw new CohortApplicationInProgressException();
        CohortApplication previous = applicationReader.findLatestByUserId(userId).orElse(null);
        ApplicantGender declaredGender = userProfileReader.findByUserId(userId)
                .map(CohortApplicationService::declaredGender).orElse(null);

        CohortApplication application = CohortApplication.create(userId, cohort, form, declaredGender, previous, now);
        Optional<VerifiedIdentity> verified = identityService.findVerified(userId);
        if (verified.isPresent()) {
            application = application.screen(verified.get().gender(), verified.get().birthday(), track, now);
        }
        CohortApplication saved = applicationWriter.insert(application);
        ticketService.spend(new TicketSpendCommand(userId, CohortApplication.TICKETS_PER_APPLICATION,
                CohortApplication.TICKET_REF_TYPE, String.valueOf(saved.id())));
        profileService.saveApplicantContact(userId, form.school(), form.department(), form.phone());
        log.info("cohort-application apply applicationId={} userId={} cohortId={} status={} priority={}",
                saved.id(), userId, cohortId, saved.status(), saved.priority());
        return CohortApplicationResponse.of(saved, cohort, track);
    }

    /**
     * 가장 최근 신청(닫힌 것 포함)과 신청서 기본값. 앱은 상태로 화면을 고르고, 닫힌 신청이면 다시 신청할 수 있다.
     * 기본값으로 학교·학과·번호를 미리 채워 매 기수 다시 입력하지 않게 한다.
     * PASS 대기 신청이면 여기서 PASS 판정을 다시 시도한다(#512) — 본인인증 커밋 후 판정이 실패했거나,
     * 신청과 PASS 가 겹쳐 판정할 신청이 아직 안 보였을 때의 복구 경로다.
     * 트랜잭션 밖에서 돈다: 재판정은 자기 트랜잭션에서 커밋·롤백하고, 실패해도 조회는 지금 상태로 응답한다
     * (같은 트랜잭션에 묶으면 재판정 실패가 rollback-only 를 남겨 조회까지 500 이 된다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MyCohortApplicationResponse getMine(Long userId) {
        CohortApplication latest = applicationReader.findLatestByUserId(userId).orElse(null);
        if (latest != null && latest.status() == CohortApplicationStatus.IDENTITY_PENDING) {
            rescreen(userId);
            // 판정했든, 동시에 다른 경로가 먼저 바꿨든 지금 상태를 다시 읽는다
            latest = applicationReader.getById(latest.id());
        }
        UserProfile profile = userProfileReader.findByUserId(userId).orElse(null);
        return new MyCohortApplicationResponse(latest == null ? null : toResponse(latest),
                ApplicationDefaultsResponse.of(latest, profile));
    }

    /** 결제 뒤 설문 제출·수정. 진행 중인 본인 신청만. 상태 칸은 건드리지 않는 조건부 UPDATE 라 동시 취소와 섞이지 않는다. */
    @Transactional
    public CohortApplicationResponse submitSurvey(Long userId, Long applicationId,
                                                  SubmitApplicationSurveyCommand command) {
        CohortApplication application = applicationReader.getOwned(applicationId, userId);
        ApplicationSurvey survey = command.toSurvey().validated();
        if (!application.status().inProgress()
                || !applicationWriter.updateSurvey(applicationId, userId, survey, now())) {
            throw new CohortApplicationClosedException();
        }
        return toResponse(applicationReader.getById(applicationId));
    }

    /** 배정 전 본인 취소 → CANCELLED + 참가권 반환. 동시에 두 번 눌러도 반환은 한 번이다. 배정 후 취소는 #517. */
    @Transactional
    public CohortApplicationResponse cancel(Long userId, Long applicationId) {
        CohortApplication application = applicationReader.getOwned(applicationId, userId);
        if (!application.cancellableByApplicant()
                || !closingService.closeBeforeAssignment(application, CohortApplicationStatus.CANCELLED,
                ApplicationCloseReason.USER_CANCELLED, now())) {
            throw new CohortApplicationNotCancellableException();
        }
        return toResponse(applicationReader.getById(applicationId));
    }

    private void rescreen(Long userId) {
        try {
            screeningService.screenPending(userId);
        } catch (RuntimeException e) {
            log.warn("cohort-application rescreen-on-read failed userId={}", userId, e);
        }
    }

    private CohortApplicationResponse toResponse(CohortApplication application) {
        Cohort cohort = cohortReader.getById(application.cohortId());
        return CohortApplicationResponse.of(application, cohort, trackReader.getById(cohort.trackId()));
    }

    /** 신청 성별 = 온보딩 프로필 성별. 비어 있으면 PASS 판정 때 PASS 성별로 채운다. */
    private static ApplicantGender declaredGender(UserProfile profile) {
        return profile.gender() == null ? null : ApplicantGender.valueOf(profile.gender().name());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
