package org.son.haruharu.domain.replyrecommendation.implement;

/** 추천 효과 비교 그룹(이슈 #560). NONE 은 실험이 꺼져 있을 때 남기는 값이다. */
public enum ExperimentArm {
    TREATMENT,
    HOLDOUT,
    NONE
}
