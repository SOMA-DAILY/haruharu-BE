package org.son.haruharu.domain.matching.implement;

public enum MatchingQueueStatus {
    QUEUED,
    MATCHED,
    /** 유저가 직접 취소 */
    CANCELLED,
    /** 매칭 배치에서 성사되지 못하고 만료(환불 대상) */
    EXPIRED
}
