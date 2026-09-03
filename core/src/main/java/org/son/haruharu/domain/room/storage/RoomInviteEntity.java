package org.son.haruharu.domain.room.storage;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnDefault;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_invites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomInviteEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomInviteStatus status;

    // 관리자가 정한 실제 방 정원. null이면 무제한이며, join 시 active membership과 비교한다.
    @Column
    private Integer maxUses;

    @ColumnDefault("0")
    @Column(nullable = false)
    private int usedCount;

    @Column
    private LocalDateTime expiresAt;

    // 발급 주체: admin id 또는 "SYSTEM"(자동생성 대비).
    @Column(length = 50)
    private String createdBy;

    public static RoomInviteEntity from(RoomInvite invite) {
        RoomInviteEntity entity = new RoomInviteEntity();
        entity.id = invite.id();
        entity.roomId = invite.roomId();
        entity.code = invite.code();
        entity.status = invite.status();
        entity.maxUses = invite.maxUses();
        entity.usedCount = invite.usedCount();
        entity.expiresAt = invite.expiresAt();
        entity.createdBy = invite.createdBy();
        return entity;
    }

    public RoomInvite toDomain() {
        return new RoomInvite(id, roomId, code, status, maxUses, usedCount, expiresAt, createdBy);
    }
}
