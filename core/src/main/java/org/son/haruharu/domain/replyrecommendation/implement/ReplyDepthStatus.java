package org.son.haruharu.domain.replyrecommendation.implement;

/** OBSERVED 는 depth·width 가 확정된 라벨, UNOBSERVED 는 연결이 끊기거나 불명확해 값이 없는(NULL, 0 과 다름) 라벨이다. */
public enum ReplyDepthStatus {
    OBSERVED,
    UNOBSERVED
}
