package org.son.haruharu.domain.room.implement;

import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    List<Room> findEnded(List<Long> ids, java.time.LocalDateTime now, Long beforeId, int size);

    Room save(Room room);

    Optional<Room> findById(Long id);

    List<Room> findByIdIn(List<Long> ids);

    Optional<Room> findByIdForUpdate(Long id);

    List<Room> findByIdInAndStatusNot(List<Long> ids, RoomStatus status);

    /** 구 모델 전용 배치 대상(#320) — starts_at 이 없는 ACTIVE 방. */
    List<Room> findLegacyActive();

    /** reconciler 대상(#320) — 스케줄 시각 보유 + 미확정(ENDED·CANCELLED 제외). */
    List<Room> findReconcilable();
}
