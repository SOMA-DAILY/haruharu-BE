package org.son.haruharu.domain.replyrecommendation.implement;

/** UNAVAILABLE 일 때 앱에 주는 이유. 앱은 모두 "추천 숨김"으로 처리하고 직접 답장은 그대로 둔다. */
public enum RecommendationReason {
    NOT_REQUESTED,
    FEATURE_DISABLED,
    NOT_CONFIGURED,
    OWN_VIDEO,
    VIDEO_UNAVAILABLE,
    BLOCKED,
    OWNER_WITHDRAWN,
    OWNER_LEFT,
    ROOM_CLOSED,
    BUDGET_EXCEEDED,
    ANALYSIS_FAILED,
    GENERATION_FAILED,
    TIMED_OUT,
    INVALIDATED
}
