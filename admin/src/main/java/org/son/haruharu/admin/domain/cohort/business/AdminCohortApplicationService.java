package org.son.haruharu.admin.domain.cohort.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.cohort.business.dto.AdminCohortApplicationResponse;
import org.son.haruharu.admin.domain.cohort.implement.AdminCohortApplicationReader;
import org.son.haruharu.admin.domain.cohort.implement.CohortApplicant;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.exception.CohortApplicationNotApprovableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 어드민 기수 신청자 표 조회·메모·확인 필요 승인(#515). 방에 넣기는 #516, 운영진 제외는 #518. */
@Service
@Transactional(readOnly = true)
public class AdminCohortApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    // page × size 가 int 를 넘지 않게(기수 신청자는 수백 명 단위라 닿지 않는다).
    private static final int MAX_PAGE = 10_000;

    private final AdminCohortApplicationReader adminReader;
    private final CohortReader cohortReader;
    private final CohortApplicationReader applicationReader;
    private final CohortApplicationWriter applicationWriter;
    private final Clock clock;

    public AdminCohortApplicationService(AdminCohortApplicationReader adminReader, CohortReader cohortReader,
                                         CohortApplicationReader applicationReader,
                                         CohortApplicationWriter applicationWriter, Clock clock) {
        this.adminReader = adminReader;
        this.cohortReader = cohortReader;
        this.applicationReader = applicationReader;
        this.applicationWriter = applicationWriter;
        this.clock = clock;
    }

    public PageResponse<AdminCohortApplicationResponse> getApplications(Long cohortId, CohortApplicationStatus status,
                                                                        ApplicantGender gender, int page, int size) {
        cohortReader.getById(cohortId);
        int safePage = Math.min(Math.max(0, page), MAX_PAGE);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        var content = adminReader.findApplicants(cohortId, status, gender, safePage * safeSize, safeSize).stream()
                .map(this::toResponse).toList();
        return PageResponse.of(content, safePage, safeSize, adminReader.countApplicants(cohortId, status, gender));
    }

    /** 메모는 상태와 상관없이 고친다. 빈 값이면 지운다. */
    @Transactional
    public AdminCohortApplicationResponse updateMemo(Long applicationId, String memo) {
        applicationReader.getById(applicationId);
        applicationWriter.updateMemo(applicationId, memo == null || memo.isBlank() ? null : memo.strip(),
                LocalDateTime.now(clock));
        return reload(applicationId);
    }

    /** 확인 필요 → 편성 대기. 그 사이 본인이 취소했으면 409. */
    @Transactional
    public AdminCohortApplicationResponse approve(Long applicationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        CohortApplication approved = applicationReader.getById(applicationId).approved(now);
        if (!applicationWriter.approve(approved, now)) {
            throw new CohortApplicationNotApprovableException();
        }
        return reload(applicationId);
    }

    private AdminCohortApplicationResponse reload(Long applicationId) {
        return toResponse(adminReader.applicantOf(applicationReader.getById(applicationId)));
    }

    private AdminCohortApplicationResponse toResponse(CohortApplicant row) {
        CohortApplication a = row.application();
        Integer age = a.birthday() == null ? null : LocalDateTime.now(clock).getYear() - a.birthday().getYear();
        return new AdminCohortApplicationResponse(a.id(), a.userId(), a.status(), a.reviewReason(), row.name(),
                a.declaredGender(), age, a.school(), a.department(), row.phone(), a.answers(), a.referralSource(),
                a.friendPreference(), a.motivation(), a.surveySubmittedAt(), a.priority(), a.appliedAt(),
                a.queuedAt(), a.roomId(), row.roomIds().stream().filter(id -> !id.equals(a.roomId())).toList(),
                a.closeReason(), a.adminMemo());
    }
}
