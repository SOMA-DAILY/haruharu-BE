package org.son.haruharu.domain.video.business.event;

/** 영상에 새 반응이 달릴 때 발행(자기 반응 제외). 영상 작성자에게 반응 도착 푸시. */
public record VideoReactedEvent(Long authorUserId, Long videoPostId) {
}
