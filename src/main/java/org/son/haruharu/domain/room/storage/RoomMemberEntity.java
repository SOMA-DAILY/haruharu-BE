package org.son.haruharu.domain.room.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.RoomMember;

import java.util.UUID;

@Entity
@Table(name = "room_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String gender;

    public static RoomMemberEntity from(RoomMember member) {
        RoomMemberEntity entity = new RoomMemberEntity();
        entity.id = member.id();
        entity.roomId = member.roomId();
        entity.userId = member.userId();
        entity.gender = member.gender();
        return entity;
    }

    public RoomMember toDomain() {
        return new RoomMember(id, roomId, userId, gender);
    }
}
