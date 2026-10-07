package org.son.haruharu.domain.mission.types.announcement.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMission;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;
import org.son.haruharu.domain.mission.types.announcement.implement.MissionRevealField;

@Entity
@Table(name = "announcement_missions", check = @CheckConstraint(name = "ck_announcement_missions_type",
        constraint = "mission_type = 'ANNOUNCEMENT'"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementMissionEntity extends BaseEntity {
    @Id
    @Column(name = "room_mission_id")
    private Long roomMissionId;

    // 복합 FK(room_mission_id, mission_type)가 다른 유형의 공통 미션에 연결하는 것을 막는다.
    @Column(name = "mission_type", nullable = false, length = 30, updatable = false)
    private String missionType = "ANNOUNCEMENT";

    @Column(name = "content", length = 500)
    private String content;

    @Column(name = "reveal_fields", length = 60)
    private String revealFields;

    public static AnnouncementMissionEntity from(AnnouncementMission mission) {
        var entity = new AnnouncementMissionEntity();
        entity.roomMissionId = mission.roomMissionId();
        entity.content = mission.definition().content();
        entity.revealFields = MissionRevealField.join(mission.definition().revealFields());
        entity.createdAt = mission.createdAt();
        entity.updatedAt = mission.updatedAt();
        return entity;
    }

    public AnnouncementMission toDomain() {
        return new AnnouncementMission(roomMissionId,
                new AnnouncementMissionDefinition(content, MissionRevealField.parseCsv(revealFields)), createdAt, updatedAt);
    }
}
