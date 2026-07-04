package org.son.haruharu.domain.matching.business.event;

import java.util.List;

/** 매칭 확정 배치가 방 하나를 만들 때마다 발행. 커밋 후 푸시 발송 등 부수효과의 트리거. */
public record RoomMatchedEvent(
        Long roomId,
        List<Long> userIds
) {
}
