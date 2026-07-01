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

import java.time.LocalDateTime;
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

    // 방 내 재화(열쇠) 잔액. 입장 시 0, 미션 영상 업로드로 획득, 프로필 해금에 사용(세션 스코프).
    @Column(nullable = false)
    private int pointSum;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    public static RoomMemberEntity from(RoomMember member) {
        RoomMemberEntity entity = new RoomMemberEntity();
        entity.id = member.id();
        entity.roomId = member.roomId();
        entity.userId = member.userId();
        entity.gender = member.gender();
        entity.pointSum = member.pointSum();
        entity.joinedAt = member.joinedAt();
        return entity;
    }

    public RoomMember toDomain() {
        return new RoomMember(id, roomId, userId, gender, pointSum, joinedAt);
    }
}
