package org.son.haruharu.domain.video.storage;

/** 반응 집계 쿼리 프로젝션. count(r)를 long으로 안전 매핑하기 위한 인터페이스 프로젝션. */
public interface ReactionCountView {
    Long getVideoPostId();

    String getReactionType();

    long getCount();
}
