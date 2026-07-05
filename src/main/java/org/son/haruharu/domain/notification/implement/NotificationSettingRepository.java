package org.son.haruharu.domain.notification.implement;

import java.util.List;
import java.util.Optional;

public interface NotificationSettingRepository {

    Optional<NotificationSetting> findByUserId(Long userId);

    NotificationSetting save(NotificationSetting setting);

    /** 발송 대상 필터용: 주어진 유저 중 푸시를 끈 유저 id만 반환. */
    List<Long> findPushDisabledUserIds(List<Long> userIds);
}
