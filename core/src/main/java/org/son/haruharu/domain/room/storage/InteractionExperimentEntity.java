package org.son.haruharu.domain.room.storage;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.InteractionExperimentAssignment;
import org.son.haruharu.domain.room.implement.InteractionExperimentVariant;

@Entity
@Table(name = "room_interaction_experiments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionExperimentEntity {
    @Id private Long roomId;
    @Column(nullable = false, length = 100) private String experimentKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private InteractionExperimentVariant variant;
    @Column(nullable = false) private LocalDateTime assignedAt;

    public static InteractionExperimentEntity from(InteractionExperimentAssignment value) {
        var entity = new InteractionExperimentEntity();
        entity.roomId = value.roomId();
        entity.experimentKey = value.experimentKey();
        entity.variant = value.variant();
        entity.assignedAt = value.assignedAt();
        return entity;
    }
    public InteractionExperimentAssignment toDomain() {
        return new InteractionExperimentAssignment(roomId, experimentKey, variant, assignedAt);
    }
}
