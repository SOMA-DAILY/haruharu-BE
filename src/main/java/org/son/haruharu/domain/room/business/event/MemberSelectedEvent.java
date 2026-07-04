package org.son.haruharu.domain.room.business.event;

/** 누군가 나를 최종선택(아직 상호 아님)했을 때 발행. 선택받은 유저 1명에게 푸시. */
public record MemberSelectedEvent(Long roomId, Long selectedUserId) {
}
