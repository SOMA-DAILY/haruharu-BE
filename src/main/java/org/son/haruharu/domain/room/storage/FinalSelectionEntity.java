package org.son.haruharu.domain.room.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.FinalSelection;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "final_selections", uniqueConstraints = @UniqueConstraint(columnNames = "roomMemberId"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalSelectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID roomMemberId;

    @Column(nullable = false)
    private UUID selectedMemberId;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    public static FinalSelectionEntity from(FinalSelection selection) {
        FinalSelectionEntity entity = new FinalSelectionEntity();
        entity.id = selection.id();
        entity.roomMemberId = selection.roomMemberId();
        entity.selectedMemberId = selection.selectedMemberId();
        entity.submittedAt = selection.submittedAt();
        return entity;
    }

    public FinalSelection toDomain() {
        return new FinalSelection(id, roomMemberId, selectedMemberId, submittedAt);
    }
}
