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

    public List<Long> findActivityDisabledUserIds(List<Long> userIds) {
        return notificationSettingRepository.findActivityDisabledUserIds(userIds);
    }

    public List<Long> findMarketingEnabledUserIds(List<Long> userIds) {
        return notificationSettingRepository.findMarketingEnabledUserIds(userIds);
    }
}
