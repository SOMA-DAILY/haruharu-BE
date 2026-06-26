package org.son.haruharu.domain.mission.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.mission.implement.UploadSlot;

import java.util.UUID;

@Entity
@Table(name = "upload_slots", uniqueConstraints = @UniqueConstraint(columnNames = {"day_number", "slot_index"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadSlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private int slotIndex;

    @Column(nullable = false)
    private int startHour;

    @Column(nullable = false)
    private int endHour;

    public static UploadSlotEntity from(UploadSlot slot) {
        UploadSlotEntity entity = new UploadSlotEntity();
        entity.id = slot.id();
        entity.dayNumber = slot.dayNumber();
        entity.slotIndex = slot.slotIndex();
        entity.startHour = slot.startHour();
        entity.endHour = slot.endHour();
        return entity;
    }

    public UploadSlot toDomain() {
        return new UploadSlot(id, dayNumber, slotIndex, startHour, endHour);
    }
}
