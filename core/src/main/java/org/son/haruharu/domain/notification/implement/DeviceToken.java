package org.son.haruharu.domain.notification.implement;

public record DeviceToken(
        Long id,
        Long userId,
        String token
) {
    public static DeviceToken of(Long userId, String token) {
        return new DeviceToken(null, userId, token);
    }

    /** 같은 기기를 다른 계정이 사용하게 된 경우 소유자를 이전한다(토큰은 기기당 1개). */
    public DeviceToken reassignedTo(Long userId) {
        return new DeviceToken(id, userId, token);
    }
}
