package org.son.haruharu.domain.notification.storage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingJpaRepository extends JpaRepository<NotificationSettingEntity, Long> {

    Optional<NotificationSettingEntity> findByUserId(Long userId);

    @Query("select s.userId from NotificationSettingEntity s where s.userId in :userIds and s.pushEnabled = false")
    List<Long> findPushDisabledUserIds(@Param("userIds") List<Long> userIds);
}
