package org.son.haruharu.domain.room.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.hibernate.annotations.ColumnDefault;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
// 5분 주기 reconciler 가 (starts_at IS NOT NULL, status NOT IN(...)) 로 훑으므로
// 방이 늘어도 풀스캔하지 않도록 복합 인덱스를 명시한다(#320).
@Table(name = "rooms", indexes = {
        @Index(name = "idx_rooms_reconcile", columnList = "starts_at, status"),
        @Index(name = "idx_rooms_status", columnList = "status"),
        @Index(name = "idx_rooms_is_test_id", columnList = "is_test, id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "is_test", nullable = false)
    @ColumnDefault("false")
    private boolean isTest;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private int totalDays;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    // 초대코드 모델(#311): 운영진/어드민이 방마다 입력하는 스케줄 시각. 구 모델 방은 전부 null.
    @Column
    private LocalDateTime startsAt;

    @Column
    private LocalDateTime finalSelectionOpensAt;

    @Column
    private LocalDateTime finalSelectionClosesAt;

    @Column
    private LocalDateTime resultAt;

    @Column
    private LocalDateTime endsAt;

    public static RoomEntity from(Room room) {
        RoomEntity entity = new RoomEntity();
        entity.id = room.id();
        entity.status = room.status();
        entity.isTest = room.isTest();
        entity.scheduledDate = room.scheduledDate();
        entity.totalDays = room.totalDays();
        entity.startedAt = room.startedAt();
        entity.endedAt = room.endedAt();
        entity.startsAt = room.startsAt();
        entity.finalSelectionOpensAt = room.finalSelectionOpensAt();
        entity.finalSelectionClosesAt = room.finalSelectionClosesAt();
        entity.resultAt = room.resultAt();
        entity.endsAt = room.endsAt();
        return entity;
    }

    public Room toDomain() {
        return new Room(id, status, scheduledDate, totalDays, startedAt, endedAt,
                startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt, isTest);
    }
}
