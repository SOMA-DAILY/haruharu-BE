package org.son.haruharu.domain.replyrecommendation.implement;

/** 앱에 내보내는 추천 상태. 명세 9장의 상태별 규칙을 따른다. */
public enum RecommendationStatus {
    PENDING,
    READY,
    EMPTY,
    UNAVAILABLE
}
