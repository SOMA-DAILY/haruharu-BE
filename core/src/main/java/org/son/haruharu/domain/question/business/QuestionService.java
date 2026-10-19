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
                c.eligibleGender()
                        && c.openDay()
                                .map(
                                        day ->
                                                reader.grant(roomId, userId, day.day())
                                                        .map(g -> !g.read())
                                                        .orElse(true))
                                .orElse(false);
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
        if (c.eligibleGender())
            c.openDay()
                    .ifPresent(
                            day ->
                                    writer.readGrant(
                                            roomId, userId, day.day(), LocalDateTime.now(clock)));
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

    /** 그날 질문이 열려 있으면(2·3일차 08:00부터) 참가자마다 그날 열림을 한 번씩 지급하고 알린다. */
    @Transactional
    public void reconcileGrants(Long roomId) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 매 10초 스캔이라 열린 날이 없으면 방 잠금 없이 끝낸다.
        Room unlocked = rooms.findById(roomId).orElse(null);
        if (unlocked == null || window(unlocked, now).openDay().isEmpty()) return;
        Room room = rooms.findByIdForUpdate(roomId).orElseThrow(QuestionAccessDeniedException::new);
        Optional<QuestionWindow.Day> open = window(room, now).openDay();
        if (open.isEmpty()) return;
        int day = open.get().day();
        List<RoomMember> roomMembers = members.getByRoomId(roomId);
        Set<Long> active =
                users.findActiveIds(roomMembers.stream().map(RoomMember::userId).toList());
        for (RoomMember member : roomMembers) {
            if (participant(member).eligibleForGrant(active)
                    && writer.grant(roomId, member.userId(), day, now))
                events.publishEvent(new QuestionGrantAvailableEvent(roomId, member.userId()));
        }
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
