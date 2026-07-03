package org.son.haruharu.domain.value.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.value.implement.UserValueAnswer;

import java.util.UUID;

@Entity
@Table(
        name = "user_value_answers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "question_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserValueAnswerEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "question_id", nullable = false, columnDefinition = "uuid")
    private UUID questionId;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    public static UserValueAnswerEntity from(UserValueAnswer userValueAnswer) {
        var entity = new UserValueAnswerEntity();
        entity.id = userValueAnswer.id();
        entity.userId = userValueAnswer.userId();
        entity.questionId = userValueAnswer.questionId();
        entity.answer = userValueAnswer.answer();
        return entity;
    }

    public UserValueAnswer toDomain() {
        return new UserValueAnswer(id, userId, questionId, answer);
    }
}
