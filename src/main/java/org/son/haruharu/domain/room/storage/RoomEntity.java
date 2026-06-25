package org.son.haruharu.domain.room.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private int totalDays;

    @Column(nullable = false)
    private int currentDay;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    public static RoomEntity from(Room room) {
        RoomEntity entity = new RoomEntity();
        entity.id = room.id();
        entity.status = room.status();
        entity.scheduledDate = room.scheduledDate();
        entity.totalDays = room.totalDays();
        entity.currentDay = room.currentDay();
        entity.startedAt = room.startedAt();
        entity.endedAt = room.endedAt();
        return entity;
    }

    public Room toDomain() {
        return new Room(id, status, scheduledDate, totalDays, currentDay, startedAt, endedAt);
    }
}
