package org.son.haruharu.domain.room.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.room.implement.RoomMember;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// (room_id, user_id) 유니크: 초대코드 동시 입장 중복 생성 방지 backstop(#312).
@Entity
@Table(name = "room_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_room_member_room_user", columnNames = {"room_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMemberEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long userId;

    // #312: 온보딩에서 gender를 안 받으므로 초대코드 입장 시 null 허용. 이후 운영진/PASS 인증이 채운다.
    @Column(length = 10)
    private String gender;

    // #393: 방 세션 동안 실명 대신 노출하는 가명. 성별 미상이면 배정 못 해 null.
    @Column(length = 30)
    private String pseudonym;

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
        entity.pseudonym = member.pseudonym();
        entity.birthday = member.birthday();
        entity.joinedAt = member.joinedAt();
        return entity;
    }

    public RoomMember toDomain() {
        return new RoomMember(id, roomId, userId, gender, pseudonym, birthday, joinedAt);
    }
}
