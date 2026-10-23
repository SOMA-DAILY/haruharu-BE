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
 * 채팅방. type(GROUP/DIRECT/PAIR)에 따라 room_id(GROUP·PAIR) 또는 match_result_id(DIRECT)가 채워진다.
 * DIRECT는 match_result_id 당 1방(UNIQUE). GROUP의 room_id 당 1방은 부분 유니크
 * ux_chat_rooms_group_room(WHERE type='GROUP', #540 SQL)이 맡는다 — 같은 방의 PAIR 여러 개와 함께 두기 위해서다.
 * 부분 유니크는 JPA 로 표현할 수 없어 여기 선언하지 않는다(ddl-auto=update 가 옛 전체 유니크를 되살리지 않게).
 */
@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(name = "uq_chat_rooms_match_result", columnNames = "match_result_id")
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
