package org.son.haruharu.domain.room.business.event;

/** 종료 전이 또는 수동 취소. 동기 리스너가 같은 트랜잭션에서 GROUP 채팅도 닫는다. */
public record RoomEndedEvent(Long roomId) {}
