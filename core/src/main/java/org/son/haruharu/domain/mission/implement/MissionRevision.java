package org.son.haruharu.domain.mission.implement;

import org.son.haruharu.domain.mission.implement.exception.InvalidMissionRevisionException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** 실제로 배정되는 불변 미션 버전. 새 문구나 메타데이터는 새 revision으로만 발행한다. */
public record MissionRevision(
        Long id,
        Long missionId,
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
        boolean fallback,
        LocalDateTime createdAt
) {
    private static final Pattern SEMANTIC_CLUSTER = Pattern.compile("^[A-Z][A-Z0-9_]{0,49}$");

    public static MissionRevision create(
            Long missionId, int revisionNumber, String description,
            MissionAttributeLevel disclosureDepth, MissionGoal missionGoal,
            ActivationChannel activationChannel, InteractionScope interactionScope,
            MissionAttributeLevel participationBurden, MissionAttributeLevel privacyRisk,
            PreferenceLeakRisk preferenceLeakRisk, MissionAttributeLevel captureDifficulty,
            String semanticCluster, BigDecimal baseQualityScore, boolean fallback
    ) {
        validate(missionId, revisionNumber, description, disclosureDepth, missionGoal, activationChannel,
                interactionScope, participationBurden, privacyRisk, preferenceLeakRisk, captureDifficulty,
                semanticCluster, baseQualityScore, fallback);
        return new MissionRevision(null, missionId, revisionNumber, description, disclosureDepth, missionGoal,
                activationChannel, interactionScope, participationBurden, privacyRisk, preferenceLeakRisk,
                captureDifficulty, semanticCluster, baseQualityScore, fallback, null);
    }

    private static void validate(
            Long missionId, int revisionNumber, String description,
            MissionAttributeLevel disclosureDepth, MissionGoal missionGoal,
            ActivationChannel activationChannel, InteractionScope interactionScope,
            MissionAttributeLevel participationBurden, MissionAttributeLevel privacyRisk,
            PreferenceLeakRisk preferenceLeakRisk, MissionAttributeLevel captureDifficulty,
            String semanticCluster, BigDecimal baseQualityScore, boolean fallback
    ) {
        boolean missing = missionId == null || revisionNumber <= 0 || description == null
                || description.isBlank() || description.length() > 500 || disclosureDepth == null
                || missionGoal == null || activationChannel == null || interactionScope == null
                || participationBurden == null || privacyRisk == null || preferenceLeakRisk == null
                || captureDifficulty == null || semanticCluster == null || baseQualityScore == null;
        boolean invalidCluster = semanticCluster != null
                && !SEMANTIC_CLUSTER.matcher(semanticCluster).matches();
        boolean invalidScore = baseQualityScore != null
                && (baseQualityScore.compareTo(BigDecimal.ZERO) < 0 || baseQualityScore.compareTo(BigDecimal.ONE) > 0);
        boolean invalidChatCapture = activationChannel == ActivationChannel.GROUP_CHAT
                && captureDifficulty != MissionAttributeLevel.LOW;
        boolean unsafeFallback = fallback && (disclosureDepth != MissionAttributeLevel.LOW
                || participationBurden != MissionAttributeLevel.LOW
                || privacyRisk != MissionAttributeLevel.LOW
                || preferenceLeakRisk != PreferenceLeakRisk.NONE);
        if (missing || invalidCluster || invalidScore || invalidChatCapture || unsafeFallback) {
            throw new InvalidMissionRevisionException();
        }
    }
}
