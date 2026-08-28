package org.son.haruharu.admin.domain.mission.business.dto;

import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.MissionRevisionEligibility;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record AdminMissionRevisionResponse(
        Long id,
        int revisionNumber,
        String description,
        MissionAttributeLevel disclosureDepth,
        MissionGoal missionGoal,
        ActivationChannel activationChannel,
        InteractionScope interactionScope,
        MissionAttributeLevel participationBurden,
        MissionAttributeLevel privacyRisk,
        PreferenceLeakRisk preferenceLeakRisk,
        MissionAttributeLevel captureDifficulty,
        String semanticCluster,
        BigDecimal baseQualityScore,
        boolean isFallback,
        List<AdminMissionEligibilityResponse> eligibilities,
        LocalDateTime createdAt
) {
    public static AdminMissionRevisionResponse of(
            MissionRevision revision,
            List<MissionRevisionEligibility> eligibilities
    ) {
        return new AdminMissionRevisionResponse(
                revision.id(), revision.revisionNumber(), revision.description(), revision.disclosureDepth(),
                revision.missionGoal(), revision.activationChannel(), revision.interactionScope(),
                revision.participationBurden(), revision.privacyRisk(), revision.preferenceLeakRisk(),
                revision.captureDifficulty(), revision.semanticCluster(), revision.baseQualityScore(),
                revision.fallback(), eligibilities.stream()
                .sorted(Comparator.comparingInt(MissionRevisionEligibility::dayNumber)
                        .thenComparingInt(eligibility -> eligibility.period().ordinal()))
                .map(AdminMissionEligibilityResponse::from).toList(),
                revision.createdAt());
    }
}
