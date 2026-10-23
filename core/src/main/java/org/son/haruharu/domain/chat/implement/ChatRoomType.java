package org.son.haruharu.domain.chat.implement;

/**
 * 채팅방 유형. GROUP=소개팅방 전체 6인 채팅(방 시작 시 자동 생성, room_id 연결),
 * DIRECT=최종선택 상호성사 1:1 채팅(match_result_id 연결),
 * PAIR=운영자가 정한 대화 시간에 같은 방 두 사람이 연 1:1 채팅(#540, chat_room_pairs 연결. room_id·match_result_id 는 비운다).
 */
public enum ChatRoomType {
    GROUP, DIRECT, PAIR
}
