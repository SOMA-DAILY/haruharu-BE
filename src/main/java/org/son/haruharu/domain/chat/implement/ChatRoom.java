package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/** 매칭 성사로 생성되는 1:1 채팅방. match_result_id로 매칭 결과와 연결된다. */
public record ChatRoom(
        Long id,
        Long matchResultId,
        LocalDateTime createdAt
) {
    /** 신규 방: id·createdAt은 영속화 시점에 부여된다. (자동 생성은 #61에서 사용) */
    public static ChatRoom create(Long matchResultId) {
        return new ChatRoom(null, matchResultId, null);
    }
}
