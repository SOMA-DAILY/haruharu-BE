package org.son.haruharu.domain.chat.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageReactionJpaRepository extends JpaRepository<ChatMessageReactionEntity, Long> {

    Optional<ChatMessageReactionEntity> findByMessageIdAndUserId(Long messageId, Long userId);

    // 같은 유저의 동시 첫 반응이 UNIQUE 에 걸려도 예외(→ PostgreSQL 트랜잭션 abort) 없이 0행으로 끝나게 한다.
    @Modifying
    @Query(value = "insert into chat_message_reactions (message_id, user_id, reaction_type, created_at) "
            + "values (:messageId, :userId, :reactionType, :createdAt) on conflict do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("messageId") Long messageId, @Param("userId") Long userId,
                       @Param("reactionType") String reactionType, @Param("createdAt") LocalDateTime createdAt);

    @Query("select r.messageId as messageId, r.reactionType as reactionType, count(r) as count "
            + "from ChatMessageReactionEntity r where r.messageId in :messageIds "
            + "group by r.messageId, r.reactionType")
    List<MessageReactionCountView> countByMessageIds(@Param("messageIds") Collection<Long> messageIds);

    List<ChatMessageReactionEntity> findByMessageIdInAndUserId(Collection<Long> messageIds, Long userId);

    List<ChatMessageReactionEntity> findByMessageIdOrderByIdAsc(Long messageId);
}
