package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 대화 시간 1:1 채팅(PAIR)의 원본 방과 두 사람(#540). 두 사람은 작은 id 를 low 에 두어 쌍당 한 행이다.
 * (room_id, low_user_id, high_user_id) 유일 — 같은 방의 같은 두 사람은 대화 시간이 바뀌어도 같은 채팅방을 쓴다.
 */
public record ChatRoomPair(Long chatRoomId, Long roomId, Long lowUserId, Long highUserId, LocalDateTime createdAt) {

    public ChatRoomPair {
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(lowUserId, "lowUserId");
        Objects.requireNonNull(highUserId, "highUserId");
        if (lowUserId >= highUserId) throw new IllegalArgumentException("lowUserId must be smaller than highUserId");
    }

    public static ChatRoomPair of(Long chatRoomId, Long roomId, Long userA, Long userB, LocalDateTime createdAt) {
        if (Objects.equals(userA, userB)) throw new IllegalArgumentException("pair needs two different users");
        return new ChatRoomPair(chatRoomId, roomId, Math.min(userA, userB), Math.max(userA, userB), createdAt);
    }

    /** 쌍의 두 사람(작은 id 먼저). PAIR 는 나가기가 없어 채팅방 참여자와 같다. */
    public List<Long> userIds() {
        return List.of(lowUserId, highUserId);
    }

    public boolean includes(Long userId) {
        return lowUserId.equals(userId) || highUserId.equals(userId);
    }

    /** 나머지 한 사람. 쌍에 없는 유저면 예외(호출부가 멤버십을 먼저 확인한다). */
    public Long partnerOf(Long userId) {
        if (lowUserId.equals(userId)) return highUserId;
        if (highUserId.equals(userId)) return lowUserId;
        throw new IllegalArgumentException("user is not in this pair");
    }
}
