package org.son.haruharu.domain.chat.implement;

/**
 * 채팅방 유형. GROUP=소개팅방의 모든 멤버 채팅(방 시작 시 자동 생성, room_id 연결),
 * DIRECT=최종선택 상호성사 1:1 채팅(match_result_id 연결).
 */
public enum ChatRoomType {
    GROUP, DIRECT
}
