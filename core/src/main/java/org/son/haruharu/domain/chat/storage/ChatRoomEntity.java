package org.son.haruharu.domain.chat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomType;

/**
 * 채팅방. type(GROUP/DIRECT)에 따라 room_id 또는 match_result_id 한쪽만 채워진다.
 * UNIQUE는 각 축별로 둔다 — GROUP은 room_id, DIRECT는 match_result_id 당 1방(멱등 보장).
 * 두 컬럼 모두 nullable이라 UNIQUE는 NULL 다중 허용(표준 SQL)으로 반대 type 행과 충돌하지 않는다.
 */
@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_chat_rooms_match_result", columnNames = "match_result_id"),
                @UniqueConstraint(name = "uq_chat_rooms_room", columnNames = "room_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatRoomType type;

    /** GROUP 채팅의 소개팅방 연결(DIRECT면 null). */
    @Column(name = "room_id")
    private Long roomId;

    /** DIRECT 채팅의 매칭 결과 연결(GROUP이면 null). */
    @Column(name = "match_result_id")
    private Long matchResultId;

    @Column(name = "close_at")
    private LocalDateTime closeAt;

    public static ChatRoomEntity from(ChatRoom chatRoom) {
        ChatRoomEntity entity = new ChatRoomEntity();
        entity.id = chatRoom.id();
        entity.type = chatRoom.type();
        entity.roomId = chatRoom.roomId();
        entity.matchResultId = chatRoom.matchResultId();
        entity.createdAt = chatRoom.createdAt();
        entity.closeAt = chatRoom.closeAt();
        return entity;
    }

    public ChatRoom toDomain() {
        return new ChatRoom(id, type, roomId, matchResultId, createdAt, closeAt);
    }
}
