package org.son.haruharu.domain.question.storage;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.domain.question.implement.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class QuestionRepositoryImpl implements QuestionRepository {
    private final QuestionJpaRepository jpaRepository;
    private final QuestionGrantJpaRepository grantJpaRepository;

    public Optional<Question> findById(Long id) {
        return jpaRepository.findById(id).map(QuestionEntity::toDomain);
    }

    public Question save(Question question) {
        return jpaRepository.save(QuestionEntity.from(question)).toDomain();
    }

    public List<Question> findSent(Long roomId, Long userId) {
        return jpaRepository
                .findByRoomIdAndSenderUserIdOrderByIdDesc(roomId, userId, Pageable.unpaged())
                .stream()
                .map(QuestionEntity::toDomain)
                .toList();
    }

    public Optional<Question> findByRequest(Long roomId, Long userId, java.util.UUID requestId) {
        return jpaRepository
                .findByRoomIdAndSenderUserIdAndRequestId(roomId, userId, requestId)
                .map(QuestionEntity::toDomain);
    }

    public long countSent(Long roomId, Long userId) {
        return jpaRepository.countByRoomIdAndSenderUserId(roomId, userId);
    }

    public boolean hasSentTo(Long roomId, Long userId, Long targetUserId, int day) {
        return jpaRepository.existsByRoomIdAndSenderUserIdAndRecipientUserIdAndDay(
                roomId, userId, targetUserId, day);
    }

    private java.util.Set<Long> blocked(java.util.Set<Long> blocked) {
        return blocked.isEmpty() ? java.util.Set.of(-1L) : blocked;
    }

    public List<Question> findReceived(
            Long roomId, Long userId, java.util.Set<Long> blocked, Long cursor, int limit) {
        return jpaRepository
                .findReceived(roomId, userId, blocked(blocked), cursor, PageRequest.of(0, limit))
                .stream()
                .map(QuestionEntity::toDomain)
                .toList();
    }

    public long countReceived(Long roomId, Long userId, java.util.Set<Long> blocked) {
        return jpaRepository.countReceived(roomId, userId, blocked(blocked));
    }

    public Optional<Question> latestUnread(Long roomId, Long userId, java.util.Set<Long> blocked) {
        return jpaRepository
                .latestUnread(roomId, userId, blocked(blocked), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(QuestionEntity::toDomain);
    }

    public List<Long> historyRoomIds(Long userId, Long cursor, int limit, LocalDateTime now) {
        return jpaRepository.historyRoomIds(userId, cursor, now, PageRequest.of(0, limit));
    }

    public Optional<QuestionGrant> findGrant(Long roomId, Long userId, int day) {
        return grantJpaRepository
                .findByRoomIdAndUserIdAndDay(roomId, userId, day)
                .map(QuestionGrantEntity::toDomain);
    }

    public QuestionGrant saveGrant(QuestionGrant grant) {
        return grantJpaRepository.save(QuestionGrantEntity.from(grant)).toDomain();
    }

    public List<Long> findGrantCandidateRoomIds(Long afterId, int limit, LocalDateTime now) {
        // 일차는 달력 날짜라 시작 후 몇 시간 만에 2일차가 열릴 수 있다. 열린 날인지는 reconcile 이 판정한다.
        return jpaRepository.findGrantCandidateRoomIds(afterId, now, PageRequest.of(0, limit));
    }
}
