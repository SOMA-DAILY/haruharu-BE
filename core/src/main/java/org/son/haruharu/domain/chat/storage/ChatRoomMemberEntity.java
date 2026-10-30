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
import org.son.haruharu.domain.chat.implement.ChatRoomMember;

import java.time.LocalDateTime;

@org.hibernate.annotations.SQLRestriction("member_removed_at is null")
@Entity
@Table(
        name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_chat_room_members_room_user",
                columnNames = {"chat_room_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMemberEntity {

    /** 관리자 퇴장에 따른 비노출. 원본·일반 삭제 상태는 별도로 보존한다. */
    @Column(name = "member_removed_at", updatable=false)
    private java.time.LocalDateTime memberRemovedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    public static ChatRoomMemberEntity from(ChatRoomMember member) {
        ChatRoomMemberEntity entity = new ChatRoomMemberEntity();
        entity.id = member.id();
        entity.chatRoomId = member.chatRoomId();
        entity.userId = member.userId();
        entity.joinedAt = member.joinedAt();
        entity.leftAt = member.leftAt();
        entity.lastReadMessageId = member.lastReadMessageId();
        return entity;
    }

    public ChatRoomMember toDomain() {
        return new ChatRoomMember(id, chatRoomId, userId, joinedAt, leftAt, lastReadMessageId);
    }
}
