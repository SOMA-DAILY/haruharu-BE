package org.son.haruharu.domain.video.implement;

/** 영상별·타입별 반응 집계 한 행. 피드 반응 수 노출용 배치 조회 결과 캐리어. */
public record ReactionCount(Long videoPostId, String reactionType, long count) {
}
