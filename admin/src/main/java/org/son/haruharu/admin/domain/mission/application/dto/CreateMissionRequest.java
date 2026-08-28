package org.son.haruharu.admin.domain.mission.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.son.haruharu.admin.domain.mission.business.command.CreateMissionCommand;
import org.son.haruharu.domain.mission.implement.ActivationChannel;
import org.son.haruharu.domain.mission.implement.InteractionScope;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;

import java.math.BigDecimal;
import java.util.List;

public record CreateMissionRequest(
        @Schema(description = "미션 문구", example = "오늘 먹은 가장 맛있는 음식을 보여주세요")
        @NotBlank @Size(max = 500) String description,
        @NotNull MissionAttributeLevel disclosureDepth,
        @NotNull MissionGoal missionGoal,
        @NotNull ActivationChannel activationChannel,
        @NotNull InteractionScope interactionScope,
        @NotNull MissionAttributeLevel participationBurden,
        @NotNull MissionAttributeLevel privacyRisk,
        @NotNull PreferenceLeakRisk preferenceLeakRisk,
        @NotNull MissionAttributeLevel captureDifficulty,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,49}$") String semanticCluster,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal baseQualityScore,
        @NotNull Boolean isFallback,
        @NotEmpty List<@NotNull @Valid MissionEligibilityRequest> eligibilities
) {
    public CreateMissionCommand toCommand() {
        return new CreateMissionCommand(description, disclosureDepth, missionGoal, activationChannel,
                interactionScope, participationBurden, privacyRisk, preferenceLeakRisk, captureDifficulty,
                semanticCluster, baseQualityScore, isFallback, eligibilities.stream()
                .map(MissionEligibilityRequest::toCommand).toList());
    }
}
