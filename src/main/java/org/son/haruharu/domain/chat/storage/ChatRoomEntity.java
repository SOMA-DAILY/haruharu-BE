package org.son.haruharu.domain.chat.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.chat.implement.ChatRoom;

@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_chat_rooms_match_result",
                columnNames = "match_result_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_result_id", nullable = false)
    private Long matchResultId;

    public static ChatRoomEntity from(ChatRoom chatRoom) {
        ChatRoomEntity entity = new ChatRoomEntity();
        entity.id = chatRoom.id();
        entity.matchResultId = chatRoom.matchResultId();
        return entity;
    }

    public ChatRoom toDomain() {
        return new ChatRoom(id, matchResultId, createdAt);
    }
}
