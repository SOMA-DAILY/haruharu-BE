package org.son.haruharu.infrastructure.fcm;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** FCM 키 미설정 환경(로컬·테스트)용. 발송 대신 로그만 남겨 키 없이 개발 가능하게 한다. */
@Slf4j
public class NoopPushAdapter implements PushPort {

    @Override
    public List<String> send(PushMessage message) {
        log.info("[no-op push] tokens={} title={} quiet={} groupKey={} data={}",
                message.tokens().size(), message.title(), message.style().quiet(), message.style().groupKey(),
                message.data());
        return List.of();
    }
}
