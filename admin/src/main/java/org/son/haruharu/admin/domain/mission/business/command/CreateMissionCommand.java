package org.son.haruharu.admin.domain.mission.business.command;

import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;

import java.math.BigDecimal;
import java.util.List;

public record CreateMissionCommand(
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
        boolean fallback,
        List<MissionEligibilityCommand> eligibilities
) {
}
