package org.son.haruharu.domain.notification.implement;

/** 유저별 푸시 수신 설정. 행이 없으면 기본 on으로 간주한다(가입 시 선행 생성 불필요). */
public record NotificationSetting(
        Long id,
        Long userId,
        boolean pushEnabled
) {
    public static NotificationSetting defaultOf(Long userId) {
        return new NotificationSetting(null, userId, true);
    }

    public NotificationSetting withPushEnabled(boolean enabled) {
        return new NotificationSetting(id, userId, enabled);
    }
}
