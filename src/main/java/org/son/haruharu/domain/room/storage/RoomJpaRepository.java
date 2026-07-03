package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RoomJpaRepository extends JpaRepository<RoomEntity, Long> {

    List<RoomEntity> findByIdInAndStatusNot(List<Long> ids, RoomStatus status);

    List<RoomEntity> findAllByScheduledDateAndStatus(LocalDate scheduledDate, RoomStatus status);
}
