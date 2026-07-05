package org.son.haruharu.domain.notification.business.dto;

import org.son.haruharu.domain.notification.implement.NotificationSetting;

public record NotificationSettingResponse(boolean pushEnabled) {

    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(setting.pushEnabled());
    }
}
