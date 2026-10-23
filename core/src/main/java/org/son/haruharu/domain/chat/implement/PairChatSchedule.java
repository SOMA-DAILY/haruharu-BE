package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/**
 * 채팅이 소유하는 대화 시간 조회 포트(#540). 운영자가 정한 시간은 미션(PAIR_CHAT)이 가지며,
 * 채팅 코드는 구체 미션 유형을 모르고 이 계약만 쓴다(mission-domain 의 ChatMissionLookup 과 같은 방식).
 */
public interface PairChatSchedule {
    PairChatWindow windowAt(Long roomId, LocalDateTime now);
}
