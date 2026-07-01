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
import org.son.haruharu.domain.value.implement.ValueAnswerUnlock;

import java.util.UUID;

@Entity
@Table(
        name = "value_answer_unlocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_member_id", "user_value_answer_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ValueAnswerUnlockEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "room_member_id", nullable = false, columnDefinition = "uuid")
    private UUID roomMemberId;

    @Column(name = "user_value_answer_id", nullable = false, columnDefinition = "uuid")
    private UUID userValueAnswerId;

    public static ValueAnswerUnlockEntity from(ValueAnswerUnlock unlock) {
        var entity = new ValueAnswerUnlockEntity();
        entity.id = unlock.id();
        entity.roomMemberId = unlock.roomMemberId();
        entity.userValueAnswerId = unlock.userValueAnswerId();
        return entity;
    }

    public ValueAnswerUnlock toDomain() {
        return new ValueAnswerUnlock(id, roomMemberId, userValueAnswerId, createdAt);
    }
}