package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    Room save(Room room);

    Optional<Room> findById(Long id);

    List<Room> findByIdInAndStatusNot(List<Long> ids, RoomStatus status);

    List<Room> findAllByScheduledDateLessThanEqualAndStatus(LocalDate scheduledDate, RoomStatus status);

    List<Room> findAllByStatus(RoomStatus status);
}
