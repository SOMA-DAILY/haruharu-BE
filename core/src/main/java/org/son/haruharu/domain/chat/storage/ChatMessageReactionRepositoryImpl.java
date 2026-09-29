package org.son.haruharu.domain.chat.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.chat.implement.ChatMessageReaction;
import org.son.haruharu.domain.chat.implement.ChatMessageReactionRepository;
import org.son.haruharu.domain.chat.implement.MessageReactionCount;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatMessageReactionRepositoryImpl implements ChatMessageReactionRepository {

    private final ChatMessageReactionJpaRepository jpaRepository;

    @Override
    public Optional<ChatMessageReaction> findByMessageIdAndUserId(Long messageId, Long userId) {
        return jpaRepository.findByMessageIdAndUserId(messageId, userId).map(ChatMessageReactionEntity::toDomain);
    }

    @Override
    public boolean insertIfAbsent(ChatMessageReaction reaction) {
        return jpaRepository.insertIfAbsent(reaction.messageId(), reaction.userId(), reaction.reactionType(),
                reaction.createdAt()) > 0;
    }

    @Override
    public ChatMessageReaction save(ChatMessageReaction reaction) {
        return jpaRepository.save(ChatMessageReactionEntity.from(reaction)).toDomain();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<MessageReactionCount> countByMessageIds(Collection<Long> messageIds) {
        return jpaRepository.countByMessageIds(messageIds).stream()
                .map(v -> new MessageReactionCount(v.getMessageId(), v.getReactionType(), v.getCount()))
                .toList();
    }

    @Override
    public List<ChatMessageReaction> findMineByMessageIds(Collection<Long> messageIds, Long userId) {
        return jpaRepository.findByMessageIdInAndUserId(messageIds, userId).stream()
                .map(ChatMessageReactionEntity::toDomain).toList();
    }

    @Override
    public List<ChatMessageReaction> findByMessageId(Long messageId) {
        return jpaRepository.findByMessageIdOrderByIdAsc(messageId).stream()
                .map(ChatMessageReactionEntity::toDomain).toList();
    }
}
