package org.son.haruharu.domain.question.storage;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.question.implement.Question;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "anonymous_questions",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_question_sender_target_day",
                    columnNames = {
                        "room_id",
                        "sender_user_id",
                        "recipient_user_id",
                        "question_day"
                    }),
            @UniqueConstraint(
                    name = "uq_question_request",
                    columnNames = {"room_id", "sender_user_id", "request_id"})
        },
        indexes =
                @Index(
                        name = "idx_question_recipient",
                        columnList = "room_id,recipient_user_id,id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long senderMemberId;

    @Column(nullable = false)
    private Long recipientMemberId;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false)
    private Long recipientUserId;

    @Column(nullable = false, length = 30)
    private String recipientNickname;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(nullable = false)
    private UUID requestId;

    @Column(length = 200)
    private String answer;

    private LocalDateTime answeredAt;

    @Column(nullable = false)
    private boolean recipientRead;

    @Column(nullable = false)
    private boolean senderRead;

    @Column(nullable = false)
    private boolean senderReported;

    @Column(nullable = false)
    private boolean recipientReported;

    /** 몇 일차 질문인지(2 또는 3). 상대마다 날마다 1번 규칙의 기준이다. */
    @Column(name = "question_day", nullable = false)
    private int day;

    public static QuestionEntity from(Question q) {
        QuestionEntity e = new QuestionEntity();
        e.id = q.id();
        e.roomId = q.roomId();
        e.senderMemberId = q.senderMemberId();
        e.recipientMemberId = q.recipientMemberId();
        e.senderUserId = q.senderUserId();
        e.recipientUserId = q.recipientUserId();
        e.recipientNickname = q.recipientNickname();
        e.content = q.content();
        e.requestId = q.requestId();
        e.answer = q.answer();
        e.createdAt = q.createdAt();
        e.answeredAt = q.answeredAt();
        e.recipientRead = q.recipientRead();
        e.senderRead = q.senderRead();
        e.senderReported = q.senderReported();
        e.recipientReported = q.recipientReported();
        e.day = q.day();
        return e;
    }

    public Question toDomain() {
        return new Question(
                id,
                roomId,
                senderMemberId,
                recipientMemberId,
                senderUserId,
                recipientUserId,
                recipientNickname,
                content,
                requestId,
                answer,
                createdAt,
                answeredAt,
                recipientRead,
                senderRead,
                senderReported,
                recipientReported,
                day);
    }
}
