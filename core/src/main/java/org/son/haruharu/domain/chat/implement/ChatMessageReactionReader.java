package org.son.haruharu.domain.chat.implement;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ChatMessageReactionReader {

    private final ChatMessageReactionRepository repository;

    public ChatMessageReactionReader(ChatMessageReactionRepository repository) {
        this.repository = repository;
    }

    public Optional<ChatMessageReaction> findMine(Long messageId, Long userId) {
        return repository.findByMessageIdAndUserId(messageId, userId);
    }

    public List<MessageReactionCount> countByMessageIds(Collection<Long> messageIds) {
        return repository.countByMessageIds(messageIds);
    }

    /** messageId → 내 반응 타입. */
    public Map<Long, String> findMyTypes(Collection<Long> messageIds, Long userId) {
        return repository.findMineByMessageIds(messageIds, userId).stream()
                .collect(Collectors.toMap(ChatMessageReaction::messageId, ChatMessageReaction::reactionType,
                        (a, b) -> a));
    }

    public List<ChatMessageReaction> findByMessageId(Long messageId) {
        return repository.findByMessageId(messageId);
    }

}
