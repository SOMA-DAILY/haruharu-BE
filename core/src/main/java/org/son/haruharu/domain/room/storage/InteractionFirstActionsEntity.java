package org.son.haruharu.domain.room.storage;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.InteractionFirstActions;

@Entity
@Table(name = "room_interaction_first_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionFirstActionsEntity {
    @Id private Long roomMemberId;
    private LocalDateTime firstReactedAt;
    private LocalDateTime firstVideoRepliedAt;

    public static InteractionFirstActionsEntity from(InteractionFirstActions value) {
        var entity = new InteractionFirstActionsEntity();
        entity.roomMemberId = value.roomMemberId();
        entity.firstReactedAt = value.firstReactedAt();
        entity.firstVideoRepliedAt = value.firstVideoRepliedAt();
        return entity;
    }
    public InteractionFirstActions toDomain() {
        return new InteractionFirstActions(roomMemberId, firstReactedAt, firstVideoRepliedAt);
    }
}
