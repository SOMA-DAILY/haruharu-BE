package org.son.haruharu.domain.room.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.room.implement.RoomMember;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMemberEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID roomId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 10)
    private String gender;

    // 성별·나이의 단일 출처는 본인인증(identity). 매칭 확정 시 스냅샷으로 복사해 방 세션 동안 고정.
    @Column
    private LocalDate birthday;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    public static RoomMemberEntity from(RoomMember member) {
        RoomMemberEntity entity = new RoomMemberEntity();
        entity.id = member.id();
        entity.roomId = member.roomId();
        entity.userId = member.userId();
        entity.gender = member.gender();
        entity.birthday = member.birthday();
        entity.joinedAt = member.joinedAt();
        return entity;
    }

    public RoomMember toDomain() {
        return new RoomMember(id, roomId, userId, gender, birthday, joinedAt);
    }
}
