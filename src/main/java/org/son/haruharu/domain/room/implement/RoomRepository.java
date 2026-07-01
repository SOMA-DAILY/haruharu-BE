package org.son.haruharu.domain.room.implement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository {

    Room save(Room room);

    Optional<Room> findById(UUID id);

    List<Room> findByIdInAndStatusNot(List<UUID> ids, RoomStatus status);

    List<Room> findAllByScheduledDateAndStatus(LocalDate scheduledDate, RoomStatus status);
}
