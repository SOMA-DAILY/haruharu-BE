package org.son.haruharu.domain.chat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.son.haruharu.domain.chat.implement.ChatRoomPair;

/**
 * PAIR 채팅방의 원본 방과 두 사람(#540). chat_rooms 와 1:1(chat_room_id PK).
 * (room_id, low_user_id, high_user_id) 유일 — 같은 두 사람의 동시 열기도 한 방으로 모인다.
 * 방별 조회는 이 유일 제약의 색인(room_id 가 앞 칸)을 쓰므로 room_id 색인을 따로 두지 않는다.
 */
@Entity
@Table(
        name = "chat_room_pairs",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_room_pairs_room_users",
                columnNames = {"room_id", "low_user_id", "high_user_id"})
)
@Check(name = "ck_chat_room_pairs_order", constraints = "low_user_id < high_user_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomPairEntity {

    @Id
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "low_user_id", nullable = false)
    private Long lowUserId;

    @Column(name = "high_user_id", nullable = false)
    private Long highUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatRoomPairEntity from(ChatRoomPair pair) {
        ChatRoomPairEntity entity = new ChatRoomPairEntity();
        entity.chatRoomId = pair.chatRoomId();
        entity.roomId = pair.roomId();
        entity.lowUserId = pair.lowUserId();
        entity.highUserId = pair.highUserId();
        entity.createdAt = pair.createdAt();
        return entity;
    }

    public ChatRoomPair toDomain() {
        return new ChatRoomPair(chatRoomId, roomId, lowUserId, highUserId, createdAt);
    }
}
