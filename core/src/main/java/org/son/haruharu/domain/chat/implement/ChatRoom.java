package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/**
 * 채팅방. type으로 GROUP(소개팅방 전체 6인, roomId 연결)·DIRECT(최종선택 상호성사 1:1, matchResultId 연결)를 구분한다.
 * roomId·matchResultId는 type에 따라 한쪽만 채워진다(다른 쪽 null).
 */
public record ChatRoom(
        Long id,
        ChatRoomType type,
        Long roomId,
        Long matchResultId,
        LocalDateTime createdAt
) {
    /** 소개팅방 전체 그룹채팅: 방 시작(RoomStartedEvent) 시 자동 생성. roomId당 1방(멱등). */
    public static ChatRoom createGroup(Long roomId) {
        return new ChatRoom(null, ChatRoomType.GROUP, roomId, null, null);
    }

    /** 상호매칭 성사 1:1 채팅: match_result_id 당 1방 멱등. (자동 생성은 #61에서 사용) */
    public static ChatRoom createDirect(Long matchResultId) {
        return new ChatRoom(null, ChatRoomType.DIRECT, null, matchResultId, null);
    }
}
