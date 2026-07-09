package org.son.haruharu.domain.room.business.event;

import java.util.List;

/** 위로 구름 지급 완료(#143). userIds는 이번 실행에서 새로 지급된 미매칭 멤버만 담는다(기지급 제외). */
public record MatchConsolationEvent(Long roomId, List<Long> userIds, long amount) {
}
