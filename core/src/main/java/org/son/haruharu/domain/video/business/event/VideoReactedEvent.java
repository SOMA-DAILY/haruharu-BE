package org.son.haruharu.domain.video.business.event;

/**
 * 영상에 새 반응이 달릴 때 발행(자기 반응 제외). 영상 작성자에게 반응 도착 푸시.
 * reactorNickname·reactionType(ReactionType.name())은 REACTION_RECEIVED 푸시 문구
 * ("OO님이 반응을 남겼어요 / 회원님의 영상을 궁금해해요") 치환용 — 발행 시점에 담는다(#213, CHAT과 동일 패턴).
 */
public record VideoReactedEvent(Long authorUserId, Long videoPostId, String reactorNickname, String reactionType) {
}
