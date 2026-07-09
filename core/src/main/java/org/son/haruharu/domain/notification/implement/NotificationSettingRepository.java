package org.son.haruharu.domain.notification.implement;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository {

    Optional<NotificationSetting> findByUserId(Long userId);

    NotificationSetting save(NotificationSetting setting);

    /** 발송 필터용: 주어진 유저 중 활동 알림을 끈 유저 id(opt-out 대상). */
    List<Long> findActivityDisabledUserIds(List<Long> userIds);

    /** 발송 필터용: 주어진 유저 중 혜택 알림을 켠 유저 id(opt-in 대상). */
    List<Long> findMarketingEnabledUserIds(List<Long> userIds);
}
