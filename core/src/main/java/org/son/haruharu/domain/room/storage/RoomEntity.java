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

import org.hibernate.annotations.ColumnDefault;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomStatus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
// 5분 주기 reconciler 가 (starts_at IS NOT NULL, status NOT IN(...), demo=false) 로 훑으므로
// 방이 늘어도 풀스캔하지 않도록 복합 인덱스를 명시한다(#320).
@Table(name = "rooms", indexes = {
        @Index(name = "idx_rooms_reconcile", columnList = "starts_at, status, demo"),
        @Index(name = "idx_rooms_status_demo", columnList = "status, demo")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(nullable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false)
    private int totalDays;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    // 데모 방 여부(#265). 데모 방은 시각이 아니라 demoDay로 Day를 고정하고 배치(getLegacyActive 등)에서 격리된다.
    // 기존 행 마이그레이션 안전을 위해 @ColumnDefault("false").
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean demo;

    // 데모 방의 고정 Day(1~totalDays). 비데모 방은 null(시각 파생).
    @Column
    private Integer demoDay;

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
        entity.scheduledDate = room.scheduledDate();
        entity.totalDays = room.totalDays();
        entity.startedAt = room.startedAt();
        entity.endedAt = room.endedAt();
        entity.demo = room.demo();
        entity.demoDay = room.demoDay();
        entity.startsAt = room.startsAt();
        entity.finalSelectionOpensAt = room.finalSelectionOpensAt();
        entity.finalSelectionClosesAt = room.finalSelectionClosesAt();
        entity.resultAt = room.resultAt();
        entity.endsAt = room.endsAt();
        return entity;
    }

    public Room toDomain() {
        return new Room(id, status, scheduledDate, totalDays, startedAt, endedAt, demo, demoDay,
                startsAt, finalSelectionOpensAt, finalSelectionClosesAt, resultAt, endsAt);
    }
}
