package org.son.haruharu.domain.cohort.implement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기수 신청(#511). 옛 matching_queue 를 대체한다. 신청과 동시에 참가권 1장을 쓰고, 배정 전 종료면 돌려준다.
 * 생성 뒤 상태·설문은 조건부 UPDATE 로만 바꾼다(동시 취소·배정이 서로를 덮어쓰지 않게) — 이 객체를 다시 저장하지 않는다.
 *
 * @param declaredGender 신청 성별. 프로필 성별을 옮겨 적고, 비어 있으면 PASS 성별을 쓴다
 * @param passGender     PASS 성별 원문 스냅샷(user_identities.gender). PASS 전이면 null
 * @param birthday       PASS 생년월일 스냅샷. PASS 전이면 null
 * @param answers        트랙 추가 문항의 답(문항 key → 답)
 * @param priority       편성 우선순위. 직전 신청이 미배정으로 닫혔으면 1, 아니면 0
 * @param queuedAt       편성 대기(QUEUED)가 된 시각. 대기 순서의 기준
 * @param assignedBy     "admin:{id}" 또는 "system"(#516)
 * @param refundedAt     참가권을 돌려준 시각
 */
public record CohortApplication(
        Long id,
        Long userId,
        Long cohortId,
        Long trackId,
        CohortApplicationStatus status,
        ApplicantGender declaredGender,
        String passGender,
        LocalDate birthday,
        String school,
        String department,
        Map<String, String> answers,
        String referralSource,
        FriendPreference friendPreference,
        String motivation,
        LocalDateTime surveySubmittedAt,
        int priority,
        LocalDateTime appliedAt,
        LocalDateTime queuedAt,
        ApplicationReviewReason reviewReason,
        Long roomId,
        LocalDateTime assignedAt,
        String assignedBy,
        LocalDateTime closedAt,
        ApplicationCloseReason closeReason,
        LocalDateTime refundedAt,
        String adminMemo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** 신청 한 건에 쓰는 참가권 수. 반환도 같은 수만큼 한다. */
    public static final long TICKETS_PER_APPLICATION = 1;
    /** 참가권 원장의 출처 유형. ref_id 는 신청 id 다. */
    public static final String TICKET_REF_TYPE = "COHORT_APPLICATION";
    public static final int NORMAL_PRIORITY = 0;
    public static final int PRIORITIZED = 1;
    /** passGender 칸 길이. PASS 가 예상 밖의 긴 값을 줘도 신청이 실패하지 않게 자른다. */
    static final int PASS_GENDER_MAX = 20;

    public CohortApplication {
        answers = answers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    }

    /**
     * 새 신청(PASS 대기). 신청서는 {@link ApplicationForm#validated} 를 거친 값이어야 한다.
     *
     * @param previous 이 유저의 직전 신청. 미배정으로 닫혔으면 우선순위를 준다
     */
    public static CohortApplication create(Long userId, Cohort cohort, ApplicationForm form,
                                           ApplicantGender declaredGender, CohortApplication previous,
                                           LocalDateTime now) {
        int priority = previous != null && previous.status() == CohortApplicationStatus.NOT_ASSIGNED
                ? PRIORITIZED : NORMAL_PRIORITY;
        return new CohortApplication(null, userId, cohort.id(), cohort.trackId(),
                CohortApplicationStatus.IDENTITY_PENDING, declaredGender, null, null,
                form.school(), form.department(), form.answers(), null, null, null, null,
                priority, now, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * PASS 결과로 판정한다. 성별을 해석할 수 없거나, 신청 성별과 다르거나, 연 나이가 트랙 범위 밖이면 확인 필요.
     * 아니면 편성 대기. 신청 성별이 비어 있으면 PASS 성별을 신청 성별로 쓴다.
     * PASS 대기 신청에만 쓴다 — 이미 판정된 신청을 다시 판정하지 않는다.
     */
    public CohortApplication screen(String passGender, LocalDate birthday, RecruitTrack track, LocalDateTime now) {
        if (status != CohortApplicationStatus.IDENTITY_PENDING) {
            throw new IllegalStateException("PASS 대기 신청만 판정한다: status=" + status);
        }
        ApplicantGender verified = ApplicantGender.parse(passGender).orElse(null);
        ApplicantGender declared = declaredGender != null ? declaredGender : verified;
        ApplicationReviewReason reason = null;
        if (verified == null) {
            reason = ApplicationReviewReason.UNKNOWN_GENDER;
        } else if (declared != verified) {
            reason = ApplicationReviewReason.GENDER_MISMATCH;
        } else if (birthday == null || !track.acceptsAge(yearAge(birthday, now))) {
            reason = ApplicationReviewReason.AGE_OUT_OF_RANGE;
        }
        CohortApplicationStatus next = reason == null
                ? CohortApplicationStatus.QUEUED : CohortApplicationStatus.NEEDS_REVIEW;
        return new CohortApplication(id, userId, cohortId, trackId, next, declared, truncate(passGender), birthday,
                school, department, answers, referralSource, friendPreference, motivation, surveySubmittedAt,
                priority, appliedAt, reason == null ? now : null, reason, roomId, assignedAt, assignedBy,
                closedAt, closeReason, refundedAt, adminMemo, createdAt, updatedAt);
    }

    /** 본인이 앱에서 취소할 수 있는지. 배정 후 취소는 #517 에서 연다. */
    public boolean cancellableByApplicant() {
        return status.beforeAssignment();
    }

    /** 연 나이 = 올해 − 출생연도. 모집 공고가 출생연도로 거르는 방식과 같다(방 프로필 나이 표시와도 같다). */
    static int yearAge(LocalDate birthday, LocalDateTime now) {
        return now.getYear() - birthday.getYear();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= PASS_GENDER_MAX) return value;
        return value.substring(0, PASS_GENDER_MAX);
    }
}
