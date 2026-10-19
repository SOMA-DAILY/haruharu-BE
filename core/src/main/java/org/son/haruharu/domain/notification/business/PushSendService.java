package org.son.haruharu.domain.notification.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.implement.DeviceToken;
import org.son.haruharu.domain.notification.implement.DeviceTokenReader;
import org.son.haruharu.domain.notification.implement.DeviceTokenWriter;
import org.son.haruharu.domain.notification.implement.NotificationSettingReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.infrastructure.fcm.PushMessage;
import org.son.haruharu.infrastructure.fcm.PushPort;
import org.son.haruharu.infrastructure.fcm.PushStyle;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 발송 실패를 절대 호출자에게 전파하지 않는다 — 푸시는 부수효과이며 매칭 배치·업로드 흐름을
 * 깨뜨리면 안 된다. 트랜잭션도 열지 않는다(발송 IO를 트랜잭션에 묶지 않기 위함).
 * 알림함(inboxVisible) 저장은 푸시 수신 설정과 무관하게 대상 유저 전원에게 남긴다.
 * 야간(QuietHoursProperties, 기본 00~08시 KST)에는 푸시만 건너뛰고 알림함 저장은 유지한다(#429).
 * 단 NotificationType.quietHoursExempt 타입(유저 간 실시간 상호작용)은 야간에도 푸시를 보낸다(#446).
 * 시각은 주입 Clock(ClockConfig, Asia/Seoul) 기준이다.
 * 묶음 알림(#505)은 수신자마다 문구·표시 방식이 달라 알림함 저장(saveInbox)과 푸시(push)를 나눠 호출한다.
 * data 에 pushLevel(ALERT/QUIET)·groupKey 를 실어 앱이 포그라운드 표시를 같은 기준으로 고르게 한다.
 */
@Slf4j
@Service
public class PushSendService {

    private final DeviceTokenReader deviceTokenReader;
    private final DeviceTokenWriter deviceTokenWriter;
    private final NotificationSettingReader notificationSettingReader;
    private final NotificationHistoryService notificationHistoryService;
    private final PushPort pushPort;
    private final QuietHoursProperties quietHours;
    private final Clock clock;

    public PushSendService(DeviceTokenReader deviceTokenReader, DeviceTokenWriter deviceTokenWriter,
                           NotificationSettingReader notificationSettingReader,
                           NotificationHistoryService notificationHistoryService, PushPort pushPort,
                           QuietHoursProperties quietHours, Clock clock) {
        this.deviceTokenReader = deviceTokenReader;
        this.deviceTokenWriter = deviceTokenWriter;
        this.notificationSettingReader = notificationSettingReader;
        this.notificationHistoryService = notificationHistoryService;
        this.pushPort = pushPort;
        this.quietHours = quietHours;
        this.clock = clock;
    }

    /**
     * bodyArgs는 type의 title/body에 있는 %d 등 포맷 지정자를 치환한다(환불·충전 개수 등 가변값).
     * inboxVisible 타입은 알림함에 저장(푸시 설정 무관), 푸시는 수신 허용 유저에게만 발송한다.
     */
    public void sendToUsers(List<Long> userIds, NotificationType type, Map<String, String> data, Object... bodyArgs) {
        if (userIds.isEmpty()) {
            return;
        }
        String title = format(type.title(), bodyArgs);
        String body = format(type.body(), bodyArgs);
        saveToInbox(userIds, type, title, body, data);
        dispatchPush(userIds, type, title, body, data, PushStyle.NORMAL);
    }

    /** 알림함에는 타입 기본 문구로 저장한다(묶음 푸시와 무관하게 메시지마다 한 줄). */
    public void saveInbox(List<Long> userIds, NotificationType type, Map<String, String> data, Object... bodyArgs) {
        if (userIds.isEmpty()) {
            return;
        }
        saveToInbox(userIds, type, format(type.title(), bodyArgs), format(type.body(), bodyArgs), data);
    }

    /** 알림함 저장 없이 푸시만 보낸다. 수신 설정·야간 차단은 sendToUsers 와 같다. */
    public void push(List<Long> userIds, NotificationType type, Map<String, String> data,
                     String title, String body, PushStyle style) {
        if (userIds.isEmpty()) {
            return;
        }
        dispatchPush(userIds, type, title, body, data, style);
    }

    private void saveToInbox(List<Long> userIds, NotificationType type,
                             String title, String body, Map<String, String> data) {
        if (!type.inboxVisible()) {
            return;
        }
        try {
            notificationHistoryService.saveAll(userIds, type, title, body, data);
        } catch (Exception e) {
            log.error("알림함 저장 실패: type={} userIds={}", type, userIds, e);
        }
    }

    private void dispatchPush(List<Long> userIds, NotificationType type,
                              String title, String body, Map<String, String> data, PushStyle style) {
        if (!type.quietHoursExempt() && quietHours.isQuiet(LocalTime.now(clock))) {
            log.info("야간 푸시 차단: type={} users={}", type, userIds.size());
            return;
        }
        try {
            List<Long> enabledUserIds = filterByCategory(userIds, type);
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
            payload.put("pushLevel", style.quiet() ? "QUIET" : "ALERT");
            if (style.groupKey() != null) {
                payload.put("groupKey", style.groupKey());
            }
            List<String> invalidTokens = pushPort.send(new PushMessage(tokens, title, body, payload, style));
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

    /**
     * 타입의 카테고리별로 발송 대상을 거른다.
     * - TRANSACTIONAL: 설정 무시, 전원 발송
     * - ACTIVITY(기본 on, opt-out): 명시적으로 끈 유저만 제외
     * - MARKETING(기본 off, opt-in): 명시적으로 켠 유저만 포함
     */
    private List<Long> filterByCategory(List<Long> userIds, NotificationType type) {
        return switch (type.category()) {
            case TRANSACTIONAL -> userIds;
            case ACTIVITY -> {
                List<Long> disabled = notificationSettingReader.findActivityDisabledUserIds(userIds);
                yield disabled.isEmpty()
                        ? userIds
                        : userIds.stream().filter(id -> !disabled.contains(id)).toList();
            }
            case MARKETING -> {
                List<Long> enabled = notificationSettingReader.findMarketingEnabledUserIds(userIds);
                yield userIds.stream().filter(enabled::contains).toList();
            }
        };
    }
}
