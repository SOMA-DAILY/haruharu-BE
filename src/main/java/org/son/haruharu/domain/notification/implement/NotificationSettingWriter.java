package org.son.haruharu.domain.notification.implement;

import org.springframework.stereotype.Component;

@Component
public class NotificationSettingWriter {

    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationSettingWriter(NotificationSettingRepository notificationSettingRepository) {
        this.notificationSettingRepository = notificationSettingRepository;
    }

    public NotificationSetting save(NotificationSetting setting) {
        return notificationSettingRepository.save(setting);
    }
}
