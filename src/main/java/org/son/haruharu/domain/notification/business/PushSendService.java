package org.son.haruharu.domain.notification.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.implement.DeviceToken;
import org.son.haruharu.domain.notification.implement.DeviceTokenReader;
import org.son.haruharu.domain.notification.implement.DeviceTokenWriter;
import org.son.haruharu.domain.notification.implement.NotificationSettingReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.infrastructure.fcm.PushMessage;
import org.son.haruharu.infrastructure.fcm.PushPort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 발송 실패를 절대 호출자에게 전파하지 않는다 — 푸시는 부수효과이며 매칭 배치·업로드 흐름을
 * 깨뜨리면 안 된다. 트랜잭션도 열지 않는다(발송 IO를 트랜잭션에 묶지 않기 위함).
 */
@Slf4j
@Service
public class PushSendService {

    private final DeviceTokenReader deviceTokenReader;
    private final DeviceTokenWriter deviceTokenWriter;
    private final NotificationSettingReader notificationSettingReader;
    private final PushPort pushPort;

    public PushSendService(DeviceTokenReader deviceTokenReader, DeviceTokenWriter deviceTokenWriter,
                           NotificationSettingReader notificationSettingReader, PushPort pushPort) {
        this.deviceTokenReader = deviceTokenReader;
        this.deviceTokenWriter = deviceTokenWriter;
        this.notificationSettingReader = notificationSettingReader;
        this.pushPort = pushPort;
    }

    /**
     * bodyArgs는 type의 title/body에 있는 %d 등 포맷 지정자를 치환한다(환불·충전 개수 등 가변값).
     * 지정자가 없는 타입은 bodyArgs를 넘기지 않으면 되고, 넘겨도 무시된다.
     */
    public void sendToUsers(List<Long> userIds, NotificationType type, Map<String, String> data, Object... bodyArgs) {
        try {
            List<Long> enabledUserIds = excludePushDisabled(userIds);
            if (enabledUserIds.isEmpty()) {
                return;
            }
            List<String> tokens = deviceTokenReader.findAllByUserIds(enabledUserIds).stream()
                    .map(DeviceToken::token)
                    .toList();
            if (tokens.isEmpty()) {
                return;
            }
            Map<String, String> payload = new HashMap<>(data);
            payload.put("type", type.name());
            String title = format(type.title(), bodyArgs);
            String body = format(type.body(), bodyArgs);
            List<String> invalidTokens = pushPort.send(new PushMessage(tokens, title, body, payload));
            if (!invalidTokens.isEmpty()) {
                deviceTokenWriter.deleteAllByTokens(invalidTokens);
            }
        } catch (Exception e) {
            log.error("푸시 발송 실패: type={} userIds={}", type, userIds, e);
        }
    }

    private static String format(String template, Object[] args) {
        return args.length == 0 ? template : String.format(template, args);
    }

    private List<Long> excludePushDisabled(List<Long> userIds) {
        List<Long> disabled = notificationSettingReader.findPushDisabledUserIds(userIds);
        if (disabled.isEmpty()) {
            return userIds;
        }
        return userIds.stream().filter(id -> !disabled.contains(id)).toList();
    }
}
