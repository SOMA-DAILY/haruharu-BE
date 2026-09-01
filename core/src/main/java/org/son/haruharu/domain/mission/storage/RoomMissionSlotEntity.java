package org.son.haruharu.domain.mission.storage;

import java.time.LocalDateTime;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.RoomMissionSlot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "room_mission_slots",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_room_mission_slots_room_day_period",
                columnNames = {"room_id", "day_number", "period"}),
        indexes = {
                @Index(name = "idx_room_mission_slots_opens_at", columnList = "opens_at"),
                @Index(name = "idx_room_mission_slots_closes_at", columnList = "closes_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMissionSlotEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionPeriod period;

    @Column(name = "opens_at", nullable = false)
    private LocalDateTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalDateTime closesAt;

    public static RoomMissionSlotEntity from(RoomMissionSlot slot) {
        RoomMissionSlotEntity entity = new RoomMissionSlotEntity();
        entity.id = slot.id();
        entity.roomId = slot.roomId();
        entity.dayNumber = slot.dayNumber();
        entity.period = slot.period();
        entity.opensAt = slot.opensAt();
        entity.closesAt = slot.closesAt();
        entity.createdAt = slot.createdAt();
        return entity;
    }

    public RoomMissionSlot toDomain() {
        return new RoomMissionSlot(id, roomId, dayNumber, period, opensAt, closesAt, createdAt);
    }
}
