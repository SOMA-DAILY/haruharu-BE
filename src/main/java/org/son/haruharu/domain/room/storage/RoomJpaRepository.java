package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RoomJpaRepository extends JpaRepository<RoomEntity, UUID> {

    List<RoomEntity> findByIdInAndStatusNot(List<UUID> ids, RoomStatus status);

    List<RoomEntity> findAllByScheduledDateAndStatus(LocalDate scheduledDate, RoomStatus status);
}
