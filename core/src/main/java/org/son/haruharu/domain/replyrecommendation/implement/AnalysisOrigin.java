package org.son.haruharu.domain.replyrecommendation.implement;

/** 분석 요청을 만든 곳: 게시 트랜잭션(outbox) 또는 시청자의 추천 요청. */
public enum AnalysisOrigin {
    PUBLISH,
    ENSURE
}
