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

import java.util.UUID;

@Entity
@Table(name = "missions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private boolean isActive;

    public static MissionEntity from(Mission mission) {
        MissionEntity entity = new MissionEntity();
        entity.id = mission.id();
        entity.description = mission.description();
        entity.points = mission.points();
        entity.isActive = mission.isActive();
        return entity;
    }

    public Mission toDomain() {
        return new Mission(id, description, points, isActive);
    }
}
