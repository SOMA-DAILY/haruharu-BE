package org.son.haruharu.domain.mission.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;

@Entity
@Table(
        name = "mission_revision_eligibilities",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mission_revision_eligibility_slot",
                columnNames = {"mission_revision_id", "day_number", "period"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionRevisionEligibilityEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_revision_id", nullable = false)
    private Long missionRevisionId;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MissionPeriod period;

    public static MissionRevisionEligibilityEntity from(MissionRevisionEligibility eligibility) {
        MissionRevisionEligibilityEntity entity = new MissionRevisionEligibilityEntity();
        entity.id = eligibility.id();
        entity.missionRevisionId = eligibility.missionRevisionId();
        entity.dayNumber = eligibility.dayNumber();
        entity.period = eligibility.period();
        return entity;
    }

    public MissionRevisionEligibility toDomain() {
        return new MissionRevisionEligibility(id, missionRevisionId, dayNumber, period);
    }
}
