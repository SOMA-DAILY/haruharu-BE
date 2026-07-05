package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/**
 * 채팅방 참여자. leftAt이 있으면 방을 나간 상태.
 * lastReadMessageId는 읽음 커서 — 이 값보다 큰(더 최신) 메시지가 안 읽은 메시지다.
 */
public record ChatRoomMember(
        Long id,
        Long chatRoomId,
        Long userId,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        Long lastReadMessageId
) {
    public static ChatRoomMember join(Long chatRoomId, Long userId, LocalDateTime joinedAt) {
        return new ChatRoomMember(null, chatRoomId, userId, joinedAt, null, null);
    }

    public boolean hasLeft() {
        return leftAt != null;
    }

    /** 방 나가기: leftAt 기록한 새 인스턴스 반환. */
    public ChatRoomMember leave(LocalDateTime leftAt) {
        return new ChatRoomMember(id, chatRoomId, userId, joinedAt, leftAt, lastReadMessageId);
    }

    /** 읽음 커서 전진: 기존 커서보다 뒤로만 이동(역행 방지). */
    public ChatRoomMember readUpTo(Long messageId) {
        if (lastReadMessageId != null && messageId != null && messageId <= lastReadMessageId) {
            return this;
        }
        return new ChatRoomMember(id, chatRoomId, userId, joinedAt, leftAt, messageId);
    }
}
