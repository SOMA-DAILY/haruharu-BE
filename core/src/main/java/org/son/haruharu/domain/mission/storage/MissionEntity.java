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

    // unique: 어드민 카탈로그가 문구 중복을 막는 방어망(TOCTOU 방지). 서비스의 existsByDescription
    // check-then-act과 별개로 DB가 최종 방어 — 동시 생성 레이스 시 AdminExceptionHandler가 409로 매핑.
    @Column(nullable = false, unique = true, length = 500)
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
