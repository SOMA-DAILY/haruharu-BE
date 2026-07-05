package org.son.haruharu.infrastructure.fcm;

import java.util.List;

public interface PushPort {

    /** 발송 후 더 이상 유효하지 않은(UNREGISTERED) 토큰 목록을 반환한다. 실패는 전파하지 않는다. */
    List<String> send(PushMessage message);
}