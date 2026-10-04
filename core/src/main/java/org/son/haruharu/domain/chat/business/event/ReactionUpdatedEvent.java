package org.son.haruharu.domain.chat.business.event;

import org.son.haruharu.domain.chat.business.dto.ReactionCountItem;

import java.util.List;

/** 메시지 반응 개수가 바뀜(#456). 커밋 후 /sub/chat-rooms/{id}/events 로 REACTION_UPDATED. 누가 눌렀는지는 싣지 않는다. */
public record ReactionUpdatedEvent(Long chatRoomId, Long messageId, List<ReactionCountItem> counts) {}
