package org.son.haruharu.domain.cohort.implement;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 기수 신청 상태(#511).
 * IDENTITY_PENDING → QUEUED / NEEDS_REVIEW → ASSIGNED → COMPLETED. 갈래로 CANCELLED(본인 취소)·NOT_ASSIGNED(미배정 종료).
 */
public enum CohortApplicationStatus {
    /** 결제는 했고 PASS 본인확인을 기다린다. */
    IDENTITY_PENDING,
    /** PASS 결과가 신청 정보·트랙 조건과 어긋나 운영진 확인이 필요하다. */
    NEEDS_REVIEW,
    /** 편성 대기. 운영진이 방에 넣을 수 있다. */
    QUEUED,
    /** 방에 배정됐다. */
    ASSIGNED,
    /** 배정된 방이 끝났다. */
    COMPLETED,
    /** 본인이 취소했다. */
    CANCELLED,
    /** 방에 들어가지 못하고 닫혔다(마감·운영진 제외·방 취소·기수 취소). 다음 신청에 우선순위를 준다. */
    NOT_ASSIGNED;

    /** 진행 중. 사람당 1건만 둘 수 있다(DB 부분 유니크 ux_cohort_applications_user_active). */
    public static final Set<CohortApplicationStatus> IN_PROGRESS =
            Collections.unmodifiableSet(EnumSet.of(IDENTITY_PENDING, NEEDS_REVIEW, QUEUED, ASSIGNED));

    /** 배정 전. 본인 취소·미배정 종료가 참가권을 돌려주는 구간이다. */
    public static final Set<CohortApplicationStatus> BEFORE_ASSIGNMENT =
            Collections.unmodifiableSet(EnumSet.of(IDENTITY_PENDING, NEEDS_REVIEW, QUEUED));

    public boolean inProgress() {
        return IN_PROGRESS.contains(this);
    }

    public boolean beforeAssignment() {
        return BEFORE_ASSIGNMENT.contains(this);
    }
}
