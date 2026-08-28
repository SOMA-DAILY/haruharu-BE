package org.son.haruharu.domain.mission.storage;

import org.son.haruharu.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.mission.implement.Mission;


@Entity
@Table(name = "missions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "active_revision_id")
    private Long activeRevisionId;

    @Column(nullable = false)
    private boolean active;

    public static MissionEntity from(Mission mission) {
        MissionEntity entity = new MissionEntity();
        entity.id = mission.id();
        entity.activeRevisionId = mission.activeRevisionId();
        entity.active = mission.active();
        return entity;
    }

    public Mission toDomain() {
        return new Mission(id, activeRevisionId, active);
    }
}
