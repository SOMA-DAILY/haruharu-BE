package org.son.haruharu.infrastructure.fcm;

/**
 * 기기 표시 방식(#505). quiet=true 면 소리·배너 없이 알림 목록에만 남는다(iOS passive / Android 낮음 채널).
 * groupKey 가 같은 알림은 기기에서 한 칸으로 교체된다(iOS apns-collapse-id·thread-id / Android tag). null 이면 따로 쌓인다.
 */
public record PushStyle(boolean quiet, String groupKey) {

    public static final PushStyle NORMAL = new PushStyle(false, null);

    public static PushStyle alert(String groupKey) {
        return new PushStyle(false, groupKey);
    }

    public static PushStyle quiet(String groupKey) {
        return new PushStyle(true, groupKey);
    }
}
