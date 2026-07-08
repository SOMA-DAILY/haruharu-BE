package org.son.haruharu.domain.notification.implement;

/**
 * 유저별 푸시 수신 설정. 행이 없으면 기본값(활동 on, 혜택 off)으로 간주한다(가입 시 선행 생성 불필요).
 * 혜택(마케팅)은 정보통신망법상 opt-in이므로 기본 off.
 */
public record NotificationSetting(
        Long id,
        Long userId,
        boolean activityEnabled,
        boolean marketingEnabled
) {
    public static NotificationSetting defaultOf(Long userId) {
        return new NotificationSetting(null, userId, true, false);
    }

    public NotificationSetting withPreferences(boolean activityEnabled, boolean marketingEnabled) {
        return new NotificationSetting(id, userId, activityEnabled, marketingEnabled);
    }
}
