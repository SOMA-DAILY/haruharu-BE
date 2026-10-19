package org.son.haruharu.infrastructure.fcm;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FcmAdapter implements PushPort {

    // FCM sendEachForMulticast 호출당 토큰 상한(API 제약)
    private static final int BATCH_LIMIT = 500;

    // 앱이 만드는 Android 채널(#505). 채널이 없는 이전 앱은 기본 채널로 표시된다.
    static final String ALERT_CHANNEL_ID = "haru_alert";
    static final String QUIET_CHANNEL_ID = "haru_quiet";

    private final FirebaseMessaging firebaseMessaging;

    public FcmAdapter(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public List<String> send(PushMessage message) {
        List<String> unregistered = new ArrayList<>();
        for (int from = 0; from < message.tokens().size(); from += BATCH_LIMIT) {
            List<String> chunk = message.tokens().subList(
                    from, Math.min(from + BATCH_LIMIT, message.tokens().size()));
            sendChunk(message, chunk, unregistered);
        }
        return unregistered;
    }

    private void sendChunk(PushMessage message, List<String> chunk, List<String> unregistered) {
        MulticastMessage multicast = MulticastMessage.builder()
                .addAllTokens(chunk)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .setAndroidConfig(androidConfig(message.style()))
                .setApnsConfig(apnsConfig(message.style()))
                .build();
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(multicast);
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse sendResponse = responses.get(i);
                if (sendResponse.isSuccessful()) {
                    continue;
                }
                if (sendResponse.getException() != null
                        && sendResponse.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    unregistered.add(chunk.get(i));
                } else {
                    log.warn("FCM 개별 발송 실패: {}", sendResponse.getException() != null
                            ? sendResponse.getException().getMessagingErrorCode() : "unknown");
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM 배치 발송 실패: {}건", chunk.size(), e);
        }
    }

    static AndroidConfig androidConfig(PushStyle style) {
        AndroidNotification.Builder notification = AndroidNotification.builder()
                .setChannelId(style.quiet() ? QUIET_CHANNEL_ID : ALERT_CHANNEL_ID);
        AndroidConfig.Builder config = AndroidConfig.builder();
        if (style.groupKey() != null) {
            notification.setTag(style.groupKey());
            config.setCollapseKey(style.groupKey());
        }
        return config.setNotification(notification.build()).build();
    }

    // 조용한 알림은 소리 없이 알림 목록에만 남는다(passive). 보통 알림은 기본 소리로 울린다.
    static ApnsConfig apnsConfig(PushStyle style) {
        Aps.Builder aps = Aps.builder()
                .putCustomData("interruption-level", style.quiet() ? "passive" : "active");
        if (!style.quiet()) {
            aps.setSound("default");
        }
        ApnsConfig.Builder config = ApnsConfig.builder();
        if (style.groupKey() != null) {
            aps.setThreadId(style.groupKey());
            config.putHeader("apns-collapse-id", style.groupKey());
        }
        return config.setAps(aps.build()).build();
    }
}
