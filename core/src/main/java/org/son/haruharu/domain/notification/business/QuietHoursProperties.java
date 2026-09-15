package org.son.haruharu.domain.notification.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalTime;

/**
 * 야간 푸시 차단 시간대(#429). [start, end) 구간(Asia/Seoul)에는 FCM 발송을 건너뛴다.
 * 알림함 저장은 영향받지 않는다. 기본 00:00~08:00. 막힌 푸시는 버린다(지연 발송 없음 — 후속 이슈).
 * start > end 이면 자정을 넘는 구간(예: 23:00~07:00)으로 해석한다. start == end 는 0폭 구간이라 차단하지 않는다.
 */
@ConfigurationProperties(prefix = "haruharu.notification.quiet-hours")
public record QuietHoursProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("00:00") LocalTime start,
        @DefaultValue("08:00") LocalTime end
) {
    public boolean isQuiet(LocalTime now) {
        if (!enabled || start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
