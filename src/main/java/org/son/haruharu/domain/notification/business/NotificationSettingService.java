package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.notification.business.command.UpdateNotificationSettingCommand;
import org.son.haruharu.domain.notification.business.dto.NotificationSettingResponse;
import org.son.haruharu.domain.notification.implement.NotificationSetting;
import org.son.haruharu.domain.notification.implement.NotificationSettingReader;
import org.son.haruharu.domain.notification.implement.NotificationSettingWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingReader notificationSettingReader;
    private final NotificationSettingWriter notificationSettingWriter;

    public NotificationSettingService(NotificationSettingReader notificationSettingReader,
                                      NotificationSettingWriter notificationSettingWriter) {
        this.notificationSettingReader = notificationSettingReader;
        this.notificationSettingWriter = notificationSettingWriter;
    }

    public NotificationSettingResponse getSetting(Long userId) {
        NotificationSetting setting = notificationSettingReader.findByUserId(userId)
                .orElseGet(() -> NotificationSetting.defaultOf(userId));
        return NotificationSettingResponse.from(setting);
    }

    @Transactional
    public NotificationSettingResponse updateSetting(Long userId, UpdateNotificationSettingCommand command) {
        NotificationSetting setting = notificationSettingReader.findByUserId(userId)
                .orElseGet(() -> NotificationSetting.defaultOf(userId))
                .withPushEnabled(command.pushEnabled());
        return NotificationSettingResponse.from(notificationSettingWriter.save(setting));
    }
}
