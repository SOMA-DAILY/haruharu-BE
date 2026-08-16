package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    Room save(Room room);

    Optional<Room> findById(Long id);

    List<Room> findByIdInAndStatusNot(List<Long> ids, RoomStatus status);

    /** 배치용 — 데모 방 제외(#265). */
    List<Room> findAllByScheduledDateAndStatusAndDemoFalse(LocalDate scheduledDate, RoomStatus status);

    /** 구 모델 전용 배치 대상(#320) — starts_at 이 없는 ACTIVE 방. */
    List<Room> findLegacyActive();

    /** reconciler 대상(#320) — 스케줄 시각 보유 + 미확정(ENDED·CANCELLED 제외) + 데모 제외. */
    List<Room> findReconcilable();
}
