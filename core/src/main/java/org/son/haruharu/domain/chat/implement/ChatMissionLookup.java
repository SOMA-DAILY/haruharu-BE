package org.son.haruharu.domain.chat.implement;

import java.util.Collection;
import java.util.Map;

/** 채팅이 소유한 조회 포트. 미션 구현은 한 페이지의 안내 참조를 일괄 제공한다. */
public interface ChatMissionLookup {
    Map<Long, ChatMissionReference> findByAnnouncementIds(Collection<Long> ids);
}
