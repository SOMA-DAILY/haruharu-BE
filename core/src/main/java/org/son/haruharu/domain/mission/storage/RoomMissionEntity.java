package org.son.haruharu.domain.mission.storage;

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
import org.son.haruharu.domain.mission.implement.RoomMission;

import java.time.LocalDateTime;

/**
 * 방별 미션 인스턴스. (room_id, sequence) 유니크로 방 내 순번 중복을 막는다.
 * 이 유니크가 room_id 를 leftmost prefix 로 커버하므로 room_id 단일 조회 인덱스는 별도로 두지 않는다.
 */
@Entity
@Table(
        name = "room_missions",
        uniqueConstraints = @UniqueConstraint(name = "uq_room_missions_room_seq", columnNames = {"room_id", "sequence"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMissionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "opens_at", nullable = false)
    private LocalDateTime opensAt;

    /** null이면 무기한 열림. */
    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    public static RoomMissionEntity from(RoomMission roomMission) {
        RoomMissionEntity entity = new RoomMissionEntity();
        entity.id = roomMission.id();
        entity.roomId = roomMission.roomId();
        entity.missionId = roomMission.missionId();
        entity.opensAt = roomMission.opensAt();
        entity.closesAt = roomMission.closesAt();
        entity.sequence = roomMission.sequence();
        entity.createdAt = roomMission.createdAt();
        return entity;
    }

    public RoomMission toDomain() {
        return new RoomMission(id, roomId, missionId, opensAt, closesAt, sequence, createdAt);
    }
}
