package org.son.haruharu.domain.notification.implement;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class NotificationSettingReader {

    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationSettingReader(NotificationSettingRepository notificationSettingRepository) {
        this.notificationSettingRepository = notificationSettingRepository;
    }

    public Optional<NotificationSetting> findByUserId(Long userId) {
        return notificationSettingRepository.findByUserId(userId);
    }

    public List<Long> findPushDisabledUserIds(List<Long> userIds) {
        return notificationSettingRepository.findPushDisabledUserIds(userIds);
    }
}
