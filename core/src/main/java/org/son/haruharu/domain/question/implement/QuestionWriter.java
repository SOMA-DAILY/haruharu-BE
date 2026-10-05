package org.son.haruharu.domain.question.implement;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.domain.question.implement.exception.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QuestionWriter {
    private final QuestionRepository repository;

    public Question create(
            QuestionContext context,
            Long targetId,
            String content,
            UUID requestId,
            LocalDateTime now) {
        String normalized = Question.validate(content, 50);
        java.util.Optional<Question> retry =
                repository.findByRequest(context.roomId(), context.userId(), requestId);
        if (retry.isPresent()) {
            Question previous = retry.orElseThrow(QuestionRequestConflictException::new);
            if (!previous.recipientMemberId().equals(targetId)
                    || !previous.content().equals(normalized))
                throw new QuestionRequestConflictException();
            if (!context.visible(previous)) throw new QuestionAccessDeniedException();
            return previous;
        }
        QuestionContext.Participant target = context.target(targetId);
        context.validateTarget(target);
        if (repository.hasSentTo(context.roomId(), context.userId(), target.userId()))
            throw new QuestionAlreadySentException();
        return repository.save(
                Question.create(
                        context.roomId(),
                        context.memberId(),
                        targetId,
                        context.userId(),
                        target.userId(),
                        target.nickname(),
                        normalized,
                        requestId,
                        now));
    }

    public Question answer(
            QuestionContext context, Question question, String content, LocalDateTime now) {
        if (!question.recipientUserId().equals(context.userId()))
            throw new QuestionAccessDeniedException();
        if (question.answer() != null) return question.answer(content, now);
        if (!context.canInteract(question)) throw new QuestionUnavailableException();
        Question answered = question.answer(content, now);
        return answered == question ? question : repository.save(answered);
    }

    public void read(Question question, Long userId) {
        repository.save(question.readBy(userId));
    }

    public void report(Question question, Long userId) {
        repository.save(question.reportedBy(userId));
    }

    public boolean grant(Long roomId, Long userId, LocalDateTime now) {
        if (repository.findGrant(roomId, userId).isPresent()) return false;
        repository.saveGrant(new QuestionGrant(null, roomId, userId, false, now));
        return true;
    }

    public void readGrant(Long roomId, Long userId, LocalDateTime now) {
        QuestionGrant grant =
                repository
                        .findGrant(roomId, userId)
                        .orElse(new QuestionGrant(null, roomId, userId, false, now));
        repository.saveGrant(grant.markRead());
    }
}
