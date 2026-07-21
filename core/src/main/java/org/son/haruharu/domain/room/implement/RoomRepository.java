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

    /** 배치용 — 데모 방 제외(#265). */
    List<Room> findAllByStatusAndDemoFalse(RoomStatus status);
}
