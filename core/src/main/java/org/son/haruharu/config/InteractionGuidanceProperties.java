package org.son.haruharu.config;

import java.time.LocalDateTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import org.son.haruharu.domain.room.implement.Room;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "haruharu.interaction-guidance")
public record InteractionGuidanceProperties(
        @DefaultValue("false") boolean enabled,
        LocalDateTime enrollmentStartsAt,
        @DefaultValue("50") @Min(0) @Max(100) int tooltipPercent,
        @DefaultValue("first-video-interaction-v1") @NotBlank String experimentKey
) {
    @AssertTrue(message = "enrollment-starts-at is required when interaction guidance is enabled")
    public boolean isEnrollmentConfigured() {
        return !enabled || enrollmentStartsAt != null;
    }

    public boolean enrolls(Room room) {
        return enabled && enrollmentStartsAt != null && room.startsAt() != null
                && !room.startsAt().isBefore(enrollmentStartsAt);
    }
}
