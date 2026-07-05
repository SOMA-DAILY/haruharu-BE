package org.son.haruharu.domain.room.business.event;

/** 방이 09:00에 ACTIVE로 전환될 때 발행. 커밋 후 방 시작 푸시의 트리거. */
public record RoomStartedEvent(Long roomId) {
}
