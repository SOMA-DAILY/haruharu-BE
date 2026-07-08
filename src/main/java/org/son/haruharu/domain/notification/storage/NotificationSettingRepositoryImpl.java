package org.son.haruharu.domain.notification.storage;

import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.notification.implement.NotificationSetting;
import org.son.haruharu.domain.notification.implement.NotificationSettingRepository;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationSettingRepositoryImpl implements NotificationSettingRepository {

    private final NotificationSettingJpaRepository jpaRepository;

    public NotificationSettingRepositoryImpl(NotificationSettingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<NotificationSetting> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId).map(NotificationSettingEntity::toDomain);
    }

    @Override
    public NotificationSetting save(NotificationSetting setting) {
        return jpaRepository.save(NotificationSettingEntity.from(setting)).toDomain();
    }

    @Override
    public List<Long> findActivityDisabledUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findActivityDisabledUserIds(userIds);
    }

    @Override
    public List<Long> findMarketingEnabledUserIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findMarketingEnabledUserIds(userIds);
    }
}
