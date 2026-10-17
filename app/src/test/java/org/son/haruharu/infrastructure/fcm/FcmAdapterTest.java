package org.son.haruharu.infrastructure.fcm;

import com.google.api.client.json.gson.GsonFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** FCM 이 실제로 받는 플랫폼별 JSON 으로 표시 방식을 확인한다(#505). */
class FcmAdapterTest {

    private static String json(Object config) throws IOException {
        return GsonFactory.getDefaultInstance().toString(config);
    }

    @Test
    @DisplayName("보통 알림: Android 알림 채널, iOS 소리·active, 묶음 키가 없으면 교체 키도 없다")
    void givenNormal_whenConfig_thenAlertChannelAndSound() throws IOException {
        String android = json(FcmAdapter.androidConfig(PushStyle.NORMAL));
        String apns = json(FcmAdapter.apnsConfig(PushStyle.NORMAL));

        assertThat(android).contains("\"channel_id\":\"haru_alert\"").doesNotContain("\"tag\"").doesNotContain("collapse_key");
        assertThat(apns).contains("\"interruption-level\":\"active\"").contains("\"sound\":\"default\"")
                .doesNotContain("apns-collapse-id").doesNotContain("thread-id");
    }

    @Test
    @DisplayName("조용한 묶음 알림: Android 조용한 채널·tag, iOS passive·소리 없음·같은 collapse-id")
    void givenQuietBundle_whenConfig_thenQuietChannelAndCollapse() throws IOException {
        PushStyle style = PushStyle.quiet("chat:9");

        String android = json(FcmAdapter.androidConfig(style));
        String apns = json(FcmAdapter.apnsConfig(style));

        assertThat(android).contains("\"channel_id\":\"haru_quiet\"").contains("\"tag\":\"chat:9\"")
                .contains("\"collapse_key\":\"chat:9\"");
        assertThat(apns).contains("\"interruption-level\":\"passive\"").doesNotContain("sound")
                .contains("\"thread-id\":\"chat:9\"").contains("\"apns-collapse-id\":\"chat:9\"");
    }
}
