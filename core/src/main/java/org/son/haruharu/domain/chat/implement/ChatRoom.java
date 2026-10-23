package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/**
 * 채팅방. type으로 GROUP(소개팅방 전체 6인, roomId 연결)·DIRECT(최종선택 상호성사 1:1, matchResultId 연결)·
 * PAIR(대화 시간 1:1, ChatRoomPair 연결)를 구분한다. roomId·matchResultId는 GROUP·DIRECT 에서 한쪽만 채워지고 PAIR 는 둘 다 null.
 */
public record ChatRoom(
        Long id,
        ChatRoomType type,
        Long roomId,
        Long matchResultId,
        LocalDateTime createdAt,
        LocalDateTime closeAt
) {
    public ChatRoom(Long id, ChatRoomType type, Long roomId, Long matchResultId, LocalDateTime createdAt) {
        this(id, type, roomId, matchResultId, createdAt, null);
    }

    /** 실제 종료 처리 시각. 예정 종료 시각은 소개팅방이 관리한다. */
    public ChatRoom close(LocalDateTime at) {
        if (closeAt != null) return this;
        return new ChatRoom(id, type, roomId, matchResultId, createdAt, java.util.Objects.requireNonNull(at));
    }

    /** 소개팅방 전체 그룹채팅: 방 시작(RoomStartedEvent) 시 자동 생성. roomId당 1방(멱등). */
    public static ChatRoom createGroup(Long roomId) {
        return new ChatRoom(null, ChatRoomType.GROUP, roomId, null, null);
    }

    /** 상호매칭 성사 1:1 채팅: match_result_id 당 1방 멱등. (자동 생성은 #61에서 사용) */
    public static ChatRoom createDirect(Long matchResultId) {
        return new ChatRoom(null, ChatRoomType.DIRECT, null, matchResultId, null);
    }

    /**
     * 대화 시간 1:1 채팅(#540). 원본 방과 두 사람은 {@link ChatRoomPair}가 가진다.
     * chat_rooms.room_id 는 GROUP 1방 유일 제약이 걸려 있어 비워 둔다.
     */
    public static ChatRoom createPair() {
        return new ChatRoom(null, ChatRoomType.PAIR, null, null, null);
    }
}
