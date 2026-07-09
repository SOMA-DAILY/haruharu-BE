package org.son.haruharu.domain.notification.storage;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSettingJpaRepository extends JpaRepository<NotificationSettingEntity, Long> {

    Optional<NotificationSettingEntity> findByUserId(Long userId);

    /** 활동 알림을 명시적으로 끈 유저(opt-out). 행 없으면 기본 on이라 대상 아님. */
    @Query("select s.userId from NotificationSettingEntity s where s.userId in :userIds and s.activityEnabled = false")
    List<Long> findActivityDisabledUserIds(@Param("userIds") List<Long> userIds);

    /** 혜택 알림을 명시적으로 켠 유저(opt-in). 행 없으면 기본 off라 대상 아님. */
    @Query("select s.userId from NotificationSettingEntity s where s.userId in :userIds and s.marketingEnabled = true")
    List<Long> findMarketingEnabledUserIds(@Param("userIds") List<Long> userIds);
}
