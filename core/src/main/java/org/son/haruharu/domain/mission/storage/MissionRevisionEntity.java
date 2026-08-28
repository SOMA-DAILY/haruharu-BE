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
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;

import java.math.BigDecimal;

@Entity
@Table(
        name = "mission_revisions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mission_revisions_mission_number",
                columnNames = {"mission_id", "revision_number"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionRevisionEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "disclosure_depth", nullable = false, length = 16)
    private MissionAttributeLevel disclosureDepth;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_goal", nullable = false, length = 20)
    private MissionGoal missionGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "activation_channel", nullable = false, length = 20)
    private ActivationChannel activationChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_scope", nullable = false, length = 20)
    private InteractionScope interactionScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_burden", nullable = false, length = 16)
    private MissionAttributeLevel participationBurden;

    @Enumerated(EnumType.STRING)
    @Column(name = "privacy_risk", nullable = false, length = 16)
    private MissionAttributeLevel privacyRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_leak_risk", nullable = false, length = 16)
    private PreferenceLeakRisk preferenceLeakRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_difficulty", nullable = false, length = 16)
    private MissionAttributeLevel captureDifficulty;

    @Column(name = "semantic_cluster", nullable = false, length = 50)
    private String semanticCluster;

    @Column(name = "base_quality_score", nullable = false, precision = 4, scale = 3)
    private BigDecimal baseQualityScore;

    @Column(name = "is_fallback", nullable = false)
    private boolean fallback;

    public static MissionRevisionEntity from(MissionRevision revision) {
        MissionRevisionEntity entity = new MissionRevisionEntity();
        entity.id = revision.id();
        entity.missionId = revision.missionId();
        entity.revisionNumber = revision.revisionNumber();
        entity.description = revision.description();
        entity.disclosureDepth = revision.disclosureDepth();
        entity.missionGoal = revision.missionGoal();
        entity.activationChannel = revision.activationChannel();
        entity.interactionScope = revision.interactionScope();
        entity.participationBurden = revision.participationBurden();
        entity.privacyRisk = revision.privacyRisk();
        entity.preferenceLeakRisk = revision.preferenceLeakRisk();
        entity.captureDifficulty = revision.captureDifficulty();
        entity.semanticCluster = revision.semanticCluster();
        entity.baseQualityScore = revision.baseQualityScore();
        entity.fallback = revision.fallback();
        entity.createdAt = revision.createdAt();
        return entity;
    }

    public MissionRevision toDomain() {
        return new MissionRevision(id, missionId, revisionNumber, description, disclosureDepth, missionGoal,
                activationChannel, interactionScope, participationBurden, privacyRisk, preferenceLeakRisk,
                captureDifficulty, semanticCluster, baseQualityScore, fallback, createdAt);
    }
}
