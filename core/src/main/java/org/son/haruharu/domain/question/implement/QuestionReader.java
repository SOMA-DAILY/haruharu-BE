package org.son.haruharu.domain.question.implement;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.domain.question.implement.exception.QuestionAccessDeniedException;
import org.son.haruharu.domain.question.storage.exception.QuestionNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class QuestionReader {
    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionRepository repository;

    public Question get(Long id) {
        return repository.findById(id).orElseThrow(QuestionNotFoundException::new);
    }

    public Question accessible(Long id, QuestionContext context) {
        Question question = get(id);
        if (!question.roomId().equals(context.roomId())
                || !question.participant(context.userId())
                || !context.visible(question)) throw new QuestionAccessDeniedException();
        return question;
    }

    public record Page(
            List<Question> questions, long receivedCount, Question latestUnread, Long nextCursor) {}

    public Page page(QuestionContext context, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, 100));
        List<Question> received =
                repository.findReceived(
                        context.roomId(),
                        context.userId(),
                        context.blockedUserIds(),
                        cursor == null ? Long.MAX_VALUE : cursor,
                        pageSize + 1);
        Long next = received.size() > pageSize ? received.get(pageSize - 1).id() : null;
        List<Question> combined =
                new java.util.ArrayList<>(repository.findSent(context.roomId(), context.userId()));
        combined.addAll(received.stream().limit(pageSize).toList());
        return new Page(
                List.copyOf(combined),
                repository.countReceived(
                        context.roomId(), context.userId(), context.blockedUserIds()),
                repository
                        .latestUnread(context.roomId(), context.userId(), context.blockedUserIds())
                        .orElse(null),
                next);
    }

    /** 마감 임박 안내용. 방 인원이 적어 한 페이지면 충분하고, 답할 수 있는지는 컨텍스트가 판정한다. */
    public boolean hasUnanswered(QuestionContext context) {
        return repository
                .findReceived(
                        context.roomId(),
                        context.userId(),
                        context.blockedUserIds(),
                        Long.MAX_VALUE,
                        MAX_PAGE_SIZE)
                .stream()
                .anyMatch(
                        question ->
                                question.answer() == null
                                        && context.visible(question)
                                        && context.canInteract(question));
    }

    public List<Question> sent(Long roomId, Long userId) {
        return repository.findSent(roomId, userId);
    }

    public boolean isRetry(Long roomId, Long userId, java.util.UUID requestId) {
        return repository.findByRequest(roomId, userId, requestId).isPresent();
    }

    public List<Long> historyRoomIds(
            Long userId, Long cursor, int size, java.time.LocalDateTime now) {
        return repository.historyRoomIds(
                userId,
                cursor == null ? Long.MAX_VALUE : cursor,
                Math.max(1, Math.min(size, 100)) + 1,
                now);
    }

    public List<Long> grantCandidates(Long afterId, int limit, java.time.LocalDateTime now) {
        return repository.findGrantCandidateRoomIds(afterId, limit, now);
    }

    public Optional<QuestionGrant> grant(Long roomId, Long userId) {
        return repository.findGrant(roomId, userId);
    }
}
