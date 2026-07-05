package org.son.haruharu.infrastructure.fcm;

import java.util.List;
import java.util.Map;

/** notification(title/body)은 OS 표시용, data는 클라 딥링크 라우팅용. 개인정보 금지. */
public record PushMessage(
        List<String> tokens,
        String title,
        String body,
        Map<String, String> data
) {
}