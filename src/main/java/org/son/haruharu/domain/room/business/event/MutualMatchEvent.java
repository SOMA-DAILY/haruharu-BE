package org.son.haruharu.domain.room.business.event;

/** 최종선택이 상호 성사될 때 발행. 두 유저 모두에게 상호매칭 푸시. */
public record MutualMatchEvent(Long roomId, Long userIdA, Long userIdB) {
}
