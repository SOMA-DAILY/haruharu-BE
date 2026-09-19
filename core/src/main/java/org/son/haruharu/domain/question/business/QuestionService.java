package org.son.haruharu.domain.question.business;

import lombok.RequiredArgsConstructor;

import org.son.haruharu.common.exception.BusinessException;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.question.business.command.*;
import org.son.haruharu.domain.question.business.dto.*;
import org.son.haruharu.domain.question.implement.*;
import org.son.haruharu.domain.question.implement.event.*;
import org.son.haruharu.domain.question.implement.exception.*;
import org.son.haruharu.domain.report.implement.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.implement.UserReader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {
    private final QuestionReader reader;
    private final QuestionWriter writer;
    private final RoomReader rooms;
    private final RoomMemberReader members;
    private final UserReader users;
    private final BlockReader blocks;
    private final ReportWriter reportWriter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public QuestionListResponse list(Long roomId, Long userId) {
        return list(roomId, userId, null, 50);
    }

    public QuestionListResponse list(Long roomId, Long userId, Long cursor, int size) {
        QuestionContext c = context(roomId, userId, false);
        boolean grantUnread =
                c.open()
                        && c.eligibleGender()
                        && reader.grant(roomId, userId).map(g -> !g.read()).orElse(true);
        return QuestionListResponse.from(c, reader.page(c, cursor, size), grantUnread);
    }

    public QuestionDetailResponse detail(Long roomId, Long userId, Long id) {
        QuestionContext c = context(roomId, userId, false);
        return QuestionDetailResponse.from(reader.accessible(id, c), c);
    }

    @Transactional
    public QuestionDetailResponse create(Long roomId, Long userId, CreateQuestionCommand command) {
        QuestionContext c = context(roomId, userId, true);
        boolean retry = reader.isRetry(roomId, userId, command.requestId());
        Question q =
                writer.create(
                        c,
                        command.recipientMemberId(),
                        command.content(),
                        command.requestId(),
                        LocalDateTime.now(clock));
        if (!retry)
            events.publishEvent(
                    new QuestionCreatedEvent(
                            roomId, q.id(), q.senderUserId(), q.recipientUserId()));
        return QuestionDetailResponse.from(q, c);
    }

    @Transactional
    public QuestionDetailResponse answer(
            Long roomId, Long userId, Long id, AnswerQuestionCommand command) {
        QuestionContext c = context(roomId, userId, true);
        Question previous = reader.accessible(id, c);
        Question q = writer.answer(c, previous, command.content(), LocalDateTime.now(clock));
        if (previous.answer() == null)
            events.publishEvent(
                    new QuestionAnsweredEvent(roomId, id, q.senderUserId(), q.recipientUserId()));
        return QuestionDetailResponse.from(q, c);
    }

    @Transactional
    public void read(Long roomId, Long userId, Long id) {
        QuestionContext c = context(roomId, userId, true);
        writer.read(reader.accessible(id, c), userId);
    }

    @Transactional
    public void readGrant(Long roomId, Long userId) {
        QuestionContext c = context(roomId, userId, true);
        if (c.open() && c.eligibleGender())
            writer.readGrant(roomId, userId, LocalDateTime.now(clock));
    }

    @Transactional
    public void report(Long roomId, Long userId, Long id, ReportQuestionCommand command) {
        QuestionContext c = context(roomId, userId, true);
        Question q = reader.get(id);
        if (!q.roomId().equals(roomId)
                || !q.participant(userId)
                || c.blockedUserIds().contains(q.otherUserId(userId)))
            throw new QuestionAccessDeniedException();
        if (q.hiddenFor(userId)) return;
        q.validateReport(userId, command.reason() != null, command.detail());
        reportWriter.save(
                Report.question(
                        userId,
                        q.otherUserId(userId),
                        q.id(),
                        command.reason(),
                        command.detail(),
                        LocalDateTime.now(clock)));
        writer.report(q, userId);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean canNotify(Long roomId, Long questionId) {
        try {
            Question q = reader.get(questionId);
            if (!q.roomId().equals(roomId)) return false;
            QuestionContext c = context(roomId, q.senderUserId(), false);
            return c.canInteract(q);
        } catch (BusinessException e) {
            return false;
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean canNotifyGrant(Long roomId, Long userId) {
        try {
            QuestionContext c = context(roomId, userId, false);
            return c.open() && c.eligibleGender();
        } catch (BusinessException e) {
            return false;
        }
    }

    /** Scheduler calls bounded candidate pages, then each room in its own transaction. */
    public List<Long> grantCandidateRoomIds(Long afterId, int limit) {
        return reader.grantCandidates(
                afterId, Math.min(100, Math.max(1, limit)), LocalDateTime.now(clock));
    }

    @Transactional
    public void reconcileGrants(Long roomId) {
        Room room = rooms.findByIdForUpdate(roomId).orElseThrow(QuestionAccessDeniedException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!window(room, now).phase().equals("OPEN")) return;
        List<RoomMember> roomMembers = members.getByRoomId(roomId);
        Set<Long> active =
                users.findActiveIds(roomMembers.stream().map(RoomMember::userId).toList());
        for (RoomMember member : roomMembers) {
            if (participant(member).eligibleForGrant(active)
                    && writer.grant(roomId, member.userId(), now))
                events.publishEvent(new QuestionGrantAvailableEvent(roomId, member.userId()));
        }
    }

    /**
     * 마감 임박 안내 대상. 답할 질문이 남은 사람과 질문권을 쓰지 않은 사람을 나눠 돌려주고, 둘 다인
     * 사람은 답변 재촉에만 넣는다 — 한 사람에게 두 건을 보내지 않는다. 차단·신고·이탈·성별 규칙은
     * 목록 조회와 같은 QuestionContext 가 판정하므로 여기서 다시 정의하지 않는다.
     */
    public QuestionDeadlineTargets deadlineTargets(Long roomId) {
        QuestionDeadlineTargets none = new QuestionDeadlineTargets(List.of(), List.of());
        Room room = rooms.findById(roomId).orElse(null);
        if (room == null) return none;
        QuestionWindow window = window(room, LocalDateTime.now(clock));
        if (!window.phase().equals("OPEN")) return none;
        // 방·멤버·활성 여부는 멤버마다 달라지지 않는다. reconcileGrants 처럼 루프 밖에서 한 번만 읽는다.
        List<RoomMember> roomMembers = members.getByRoomId(roomId);
        Set<Long> active =
                users.findActiveIds(roomMembers.stream().map(RoomMember::userId).toList());
        List<QuestionContext.Participant> participants =
                roomMembers.stream().map(this::participant).toList();
        List<Long> unanswered = new ArrayList<>();
        List<Long> unusedGrant = new ArrayList<>();
        for (RoomMember member : roomMembers) {
            // 이탈·비활성 참가자는 안내 대상이 아니다.
            if (!active.contains(member.userId())) continue;
            QuestionContext context =
                    QuestionContext.forUser(
                            roomId,
                            member.userId(),
                            window,
                            participants,
                            active,
                            blocks.getBlockedUserIds(member.userId()),
                            blocks.getRelatedUserIds(member.userId()));
            if (!context.eligibleGender()) continue;
            if (reader.hasUnanswered(context)) unanswered.add(member.userId());
            else if (hasUnusedGrant(context)) unusedGrant.add(member.userId());
        }
        return new QuestionDeadlineTargets(List.copyOf(unanswered), List.copyOf(unusedGrant));
    }

    /** 남은 질문권을 실제로 쓸 수 있는지 — 보낼 수 있는 상대가 없으면 재촉하지 않는다. */
    private boolean hasUnusedGrant(QuestionContext context) {
        List<Question> sent = reader.sent(context.roomId(), context.userId());
        if (sent.size() >= 2) return false;
        List<Long> asked = sent.stream().map(Question::recipientUserId).toList();
        return context.participants().stream()
                .anyMatch(
                        participant ->
                                context.opposite(participant)
                                        && !asked.contains(participant.userId())
                                        && !context.blockedUserIds().contains(participant.userId())
                                        && !context.relatedUserIds()
                                                .contains(participant.userId()));
    }

    private QuestionContext context(Long roomId, Long userId, boolean lock) {
        Room room =
                (lock ? rooms.findByIdForUpdate(roomId) : rooms.findById(roomId))
                        .orElseThrow(QuestionAccessDeniedException::new);
        List<RoomMember> roomMembers = members.getByRoomId(roomId);
        Set<Long> active =
                users.findActiveIds(roomMembers.stream().map(RoomMember::userId).toList());
        return QuestionContext.forUser(
                roomId,
                userId,
                window(room, LocalDateTime.now(clock)),
                roomMembers.stream().map(this::participant).toList(),
                active,
                blocks.getBlockedUserIds(userId),
                blocks.getRelatedUserIds(userId));
    }

    private QuestionContext.Participant participant(RoomMember member) {
        return new QuestionContext.Participant(
                member.id(), member.userId(), member.gender(), member.displayPseudonym());
    }

    private QuestionWindow window(Room room, LocalDateTime now) {
        return QuestionWindow.at(
                room.statusAt(now).name(), room.startsAt(), room.finalSelectionOpensAt(), now);
    }

    public QuestionHistoryResponse history(Long userId, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, 100));
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> ids = reader.historyRoomIds(userId, cursor, pageSize, now);
        return QuestionHistoryResponse.from(
                rooms.findByIds(ids.stream().limit(pageSize).toList()),
                ids.size() > pageSize ? ids.get(pageSize - 1) : null,
                now);
    }
}
