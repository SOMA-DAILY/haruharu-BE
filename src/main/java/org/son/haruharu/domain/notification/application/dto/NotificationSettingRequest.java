package org.son.haruharu.domain.notification.application.dto;

import jakarta.validation.constraints.NotNull;
import org.son.haruharu.domain.notification.business.command.UpdateNotificationSettingCommand;

public record NotificationSettingRequest(
        @NotNull Boolean activityEnabled,
        @NotNull Boolean marketingEnabled) {

    public UpdateNotificationSettingCommand toCommand() {
        return new UpdateNotificationSettingCommand(activityEnabled, marketingEnabled);
    }
}
