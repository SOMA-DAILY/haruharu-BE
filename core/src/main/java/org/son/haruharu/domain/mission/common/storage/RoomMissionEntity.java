package org.son.haruharu.domain.mission.common.storage;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.common.implement.MissionImage;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry;
import org.son.haruharu.domain.mission.common.implement.RoomMission;

@Entity
@Table(name = "room_mission_instances", uniqueConstraints =
        @UniqueConstraint(name = "uq_room_mission_instances_id_room", columnNames = {"id", "room_id"}),
        indexes = @Index(name = "idx_room_mission_instances_room_open", columnList = "room_id, opens_at, id"),
        check = @CheckConstraint(name = "ck_room_mission_instances_window", constraint = "closes_at IS NULL OR closes_at > opens_at"))
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMissionEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private MissionType type;

    // 유형별 정의. 유형마다 테이블을 두지 않고 type 이 해석 방법을 정한다(PostgreSQL jsonb).
    // 공통 속성인 사진은 예약 키 image 로 같은 칸에 둔다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private Map<String, Object> details;

    @Column(name = "opens_at", nullable = false)
    private LocalDateTime opensAt;

    @Column(name = "closes_at")
    private LocalDateTime closesAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "created_by", length = 40)
    private String createdBy;

    @Column(name = "updated_by", length = 40)
    private String updatedBy;

    public static RoomMissionEntity from(RoomMission value, MissionTypeRegistry types) {
        RoomMissionEntity entity = new RoomMissionEntity();
        entity.id = value.id();
        entity.roomId = value.roomId();
        entity.type = value.type();
        entity.details = new LinkedHashMap<>(types.get(value.type()).toStored(value.details()));
        if (entity.details.containsKey(MissionImage.STORED_KEY))
            throw new IllegalStateException("Reserved mission details key: " + MissionImage.STORED_KEY);
        if (value.image() != null) entity.details.put(MissionImage.STORED_KEY, value.image().toStored());
        entity.opensAt = value.opensAt();
        entity.closesAt = value.closesAt();
        entity.canceledAt = value.canceledAt();
        entity.createdBy = value.createdBy();
        entity.updatedBy = value.updatedBy();
        entity.createdAt = value.createdAt();
        entity.updatedAt = value.updatedAt();
        return entity;
    }

    public RoomMission toDomain(MissionTypeRegistry types) {
        MissionImage image = MissionImage.fromStored(details.get(MissionImage.STORED_KEY));
        if (image == null && details.get(MissionImage.STORED_KEY) != null) {
            // 사진 없음으로 읽히고, 이후 저장하면 값이 사라진다. 흔적을 남긴다.
            log.warn("미션 details 의 image 값을 읽지 못함: missionId={}", id);
        }
        return new RoomMission(id, roomId, type, types.get(type).fromStored(details), image, opensAt, closesAt, canceledAt,
                createdBy, updatedBy, createdAt, updatedAt);
    }
}
