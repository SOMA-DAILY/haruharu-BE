package org.son.haruharu.domain.question.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository {
    Optional<Question> findById(Long id);

    Question save(Question question);

    List<Question> findSent(Long roomId, Long userId);

    Optional<Question> findByRequest(Long roomId, Long userId, java.util.UUID requestId);

    long countSent(Long roomId, Long userId);

    boolean hasSentTo(Long roomId, Long userId, Long targetUserId, int day);

    List<Question> findReceived(
            Long roomId, Long userId, java.util.Set<Long> blockedUserIds, Long cursor, int limit);

    long countReceived(Long roomId, Long userId, java.util.Set<Long> blockedUserIds);

    Optional<Question> latestUnread(Long roomId, Long userId, java.util.Set<Long> blockedUserIds);

    List<Long> historyRoomIds(Long userId, Long cursor, int limit, LocalDateTime now);

    Optional<QuestionGrant> findGrant(Long roomId, Long userId, int day);

    QuestionGrant saveGrant(QuestionGrant grant);

    List<Long> findGrantCandidateRoomIds(Long afterId, int limit, LocalDateTime now);
}
