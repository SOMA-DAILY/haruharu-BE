package org.son.haruharu.domain.cohort.implement;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.cohort.implement.exception.CohortNotCancellableException;
import org.son.haruharu.domain.cohort.implement.exception.CohortNotEditableException;
import org.son.haruharu.domain.cohort.implement.exception.CohortNotPublishableException;
import org.son.haruharu.domain.cohort.implement.exception.InvalidCohortException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 기수. 트랙 안의 한 회차("서울 대학생 6기")이고 번호는 트랙마다 따로 센다.
 * 신청 가능 = OPEN 이고 apply_opens_at ≤ now < apply_closes_at. 시각이 지나도 상태는 그대로 두고 매번 계산한다.
 *
 * @param assignDeadlineAt 운영진이 방 배정을 마쳐야 하는 시각. 이후 남은 신청은 정산에서 미배정 처리한다(#518).
 * @param createdBy        "admin:{id}" 형식
 */
public record Cohort(
        Long id,
        Long trackId,
        int sequence,
        CohortStatus status,
        LocalDateTime applyOpensAt,
        LocalDateTime applyClosesAt,
        LocalDateTime assignDeadlineAt,
        String periodLabel,
        Integer targetMale,
        Integer targetFemale,
        LocalDateTime settledAt,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static final int PERIOD_LABEL_MAX = 40;

    /** 새 기수(초안). 잘못된 칸은 모아서 한 번에 알려준다. */
    public static Cohort create(Long trackId, Integer sequence, LocalDateTime applyOpensAt,
                                LocalDateTime applyClosesAt, LocalDateTime assignDeadlineAt, String periodLabel,
                                Integer targetMale, Integer targetFemale, String createdBy) {
        List<ApiFieldError> errors = new ArrayList<>();
        if (sequence == null || sequence < 1) {
            errors.add(new ApiFieldError("sequence", "기수 번호는 1 이상이어야 합니다."));
        }
        Cohort cohort = new Cohort(null, trackId, sequence == null ? 0 : sequence, CohortStatus.DRAFT,
                applyOpensAt, applyClosesAt, assignDeadlineAt, periodLabel, targetMale, targetFemale,
                null, createdBy, null, null);
        cohort.collectErrors(errors);
        if (!errors.isEmpty()) throw new InvalidCohortException(errors);
        return cohort;
    }

    public boolean isAcceptingApplications(LocalDateTime now) {
        return status == CohortStatus.OPEN && !now.isBefore(applyOpensAt) && now.isBefore(applyClosesAt);
    }

    /** 초안을 공개한다. 신청 마감이 이미 지났으면 공개하지 않는다. */
    public Cohort publish(LocalDateTime now) {
        if (status != CohortStatus.DRAFT) throw new CohortNotPublishableException();
        if (!now.isBefore(applyClosesAt)) {
            throw new InvalidCohortException("apply_closes_at", "신청 마감 시각이 지나 공개할 수 없습니다.");
        }
        return withStatus(CohortStatus.OPEN);
    }

    /** 초안·공개 중인 기수를 취소한다. 신청자 처리(반환)는 신청 기능이 붙을 때 함께 다룬다(#511·#518). */
    public Cohort cancel() {
        if (status != CohortStatus.DRAFT && status != CohortStatus.OPEN) throw new CohortNotCancellableException();
        return withStatus(CohortStatus.CANCELLED);
    }

    /**
     * 보낸 칸(null 이 아닌 값)만 바꾼다. 공개된 기수는 앱에 "N기"로 노출됐으므로 번호는 바꾸지 않는다.
     * 정산·취소된 기수는 고치지 않는다. 공개 중 마감 시각을 앞당겨 지금 바로 신청을 닫는 것도 허용한다.
     */
    public Cohort revise(Integer sequence, LocalDateTime applyOpensAt, LocalDateTime applyClosesAt,
                         LocalDateTime assignDeadlineAt, String periodLabel, Integer targetMale, Integer targetFemale) {
        if (status != CohortStatus.DRAFT && status != CohortStatus.OPEN) throw new CohortNotEditableException();
        List<ApiFieldError> errors = new ArrayList<>();
        if (sequence != null && sequence != this.sequence) {
            if (status != CohortStatus.DRAFT) {
                errors.add(new ApiFieldError("sequence", "공개된 기수의 번호는 바꿀 수 없습니다."));
            } else if (sequence < 1) {
                errors.add(new ApiFieldError("sequence", "기수 번호는 1 이상이어야 합니다."));
            }
        }
        Cohort revised = new Cohort(id, trackId,
                sequence != null && errors.isEmpty() ? sequence : this.sequence,
                status,
                applyOpensAt != null ? applyOpensAt : this.applyOpensAt,
                applyClosesAt != null ? applyClosesAt : this.applyClosesAt,
                assignDeadlineAt != null ? assignDeadlineAt : this.assignDeadlineAt,
                periodLabel != null ? periodLabel : this.periodLabel,
                targetMale != null ? targetMale : this.targetMale,
                targetFemale != null ? targetFemale : this.targetFemale,
                settledAt, createdBy, createdAt, updatedAt);
        revised.collectErrors(errors);
        if (!errors.isEmpty()) throw new InvalidCohortException(errors);
        return revised;
    }

    /** 앱·어드민에 보이는 이름. 예: "서울 대학생 6기". */
    public String displayName(String trackLabel) {
        return trackLabel + " " + sequence + "기";
    }

    private Cohort withStatus(CohortStatus next) {
        return new Cohort(id, trackId, sequence, next, applyOpensAt, applyClosesAt, assignDeadlineAt, periodLabel,
                targetMale, targetFemale, settledAt, createdBy, createdAt, updatedAt);
    }

    private void collectErrors(List<ApiFieldError> errors) {
        if (applyOpensAt == null) {
            errors.add(new ApiFieldError("apply_opens_at", "신청 시작 시각을 입력해 주세요."));
        }
        if (applyClosesAt == null) {
            errors.add(new ApiFieldError("apply_closes_at", "신청 마감 시각을 입력해 주세요."));
        } else if (applyOpensAt != null && !applyOpensAt.isBefore(applyClosesAt)) {
            errors.add(new ApiFieldError("apply_closes_at", "신청 마감은 신청 시작보다 늦어야 합니다."));
        }
        if (assignDeadlineAt == null) {
            errors.add(new ApiFieldError("assign_deadline_at", "배정 마감 시각을 입력해 주세요."));
        } else if (applyClosesAt != null && assignDeadlineAt.isBefore(applyClosesAt)) {
            errors.add(new ApiFieldError("assign_deadline_at", "배정 마감은 신청 마감 이후여야 합니다."));
        }
        if (periodLabel == null || periodLabel.isBlank() || periodLabel.length() > PERIOD_LABEL_MAX) {
            errors.add(new ApiFieldError("period_label", "진행 기간 표기는 1~" + PERIOD_LABEL_MAX + "자여야 합니다."));
        }
        if (targetMale != null && targetMale < 0) {
            errors.add(new ApiFieldError("target_male", "목표 인원은 0 이상이어야 합니다."));
        }
        if (targetFemale != null && targetFemale < 0) {
            errors.add(new ApiFieldError("target_female", "목표 인원은 0 이상이어야 합니다."));
        }
    }
}
