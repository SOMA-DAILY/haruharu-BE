package org.son.haruharu.domain.mission.storage;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.RoomMissionSetting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 방별 미션 사용 여부(#449). 방당 1행. rooms 와 물리 FK 없이 room_id 로 논리 참조한다(도메인 분리). */
@Entity
@Table(
        name = "room_mission_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_room_mission_settings_room",
                columnNames = "room_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMissionSettingEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "mission_enabled", nullable = false)
    private boolean missionEnabled;

    public static RoomMissionSettingEntity from(RoomMissionSetting setting) {
        RoomMissionSettingEntity entity = new RoomMissionSettingEntity();
        entity.id = setting.id();
        entity.roomId = setting.roomId();
        entity.missionEnabled = setting.missionEnabled();
        entity.createdAt = setting.createdAt();
        return entity;
    }

    public RoomMissionSetting toDomain() {
        return new RoomMissionSetting(id, roomId, missionEnabled, createdAt);
    }
}
