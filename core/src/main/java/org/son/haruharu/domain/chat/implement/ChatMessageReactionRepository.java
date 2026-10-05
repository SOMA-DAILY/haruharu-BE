package org.son.haruharu.domain.chat.implement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageReactionRepository {
    Optional<ChatMessageReaction> findByMessageIdAndUserId(Long messageId, Long userId);

    /** 없을 때만 넣는다. 같은 (message_id, user_id) 가 이미 있으면 아무것도 안 하고 false. 트랜잭션을 깨지 않는다. */
    boolean insertIfAbsent(ChatMessageReaction reaction);

    ChatMessageReaction save(ChatMessageReaction reaction);

    void deleteById(Long id);

    /** 메시지 여러 개의 타입별 개수를 쿼리 1번으로. */
    List<MessageReactionCount> countByMessageIds(Collection<Long> messageIds);

    List<ChatMessageReaction> findMineByMessageIds(Collection<Long> messageIds, Long userId);

    List<ChatMessageReaction> findByMessageId(Long messageId);
}
