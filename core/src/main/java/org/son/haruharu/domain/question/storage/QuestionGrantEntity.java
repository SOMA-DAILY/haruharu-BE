package org.son.haruharu.domain.question.storage;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.question.implement.QuestionGrant;

@Entity
@Table(
        name = "question_grants",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_question_grant_day",
                        columnNames = {"room_id", "user_id", "question_day"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuestionGrantEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "question_day", nullable = false)
    private int day;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    public static QuestionGrantEntity from(QuestionGrant grant) {
        QuestionGrantEntity entity = new QuestionGrantEntity();
        entity.id = grant.id();
        entity.roomId = grant.roomId();
        entity.userId = grant.userId();
        entity.day = grant.day();
        entity.read = grant.read();
        entity.createdAt = grant.createdAt();
        return entity;
    }

    public QuestionGrant toDomain() {
        return new QuestionGrant(id, roomId, userId, day, read, createdAt);
    }
}
