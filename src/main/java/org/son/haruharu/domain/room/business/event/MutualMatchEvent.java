package org.son.haruharu.domain.room.business.event;

/** 최종선택이 상호 성사될 때 발행. 두 유저에게 상호매칭 푸시 + 채팅방 자동 생성(#61)의 트리거. */
public record MutualMatchEvent(Long roomId, Long matchResultId, Long userIdA, Long userIdB) {
}
