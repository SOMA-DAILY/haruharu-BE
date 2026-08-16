package org.son.haruharu.domain.room.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomRepository;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoomRepositoryImpl implements RoomRepository {

    private final RoomJpaRepository jpaRepository;

    @Override
    public Room save(Room room) {
        return jpaRepository.save(RoomEntity.from(room)).toDomain();
    }

    @Override
    public Optional<Room> findById(Long id) {
        return jpaRepository.findById(id).map(RoomEntity::toDomain);
    }

    @Override
    public List<Room> findByIdInAndStatusNot(List<Long> ids, RoomStatus status) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByIdInAndStatusNot(ids, status).stream()
                .map(RoomEntity::toDomain)
                .toList();
    }

    @Override
    public List<Room> findAllByScheduledDateAndStatus(LocalDate scheduledDate, RoomStatus status) {
        return jpaRepository.findAllByScheduledDateAndStatusAndStartsAtIsNull(scheduledDate, status).stream()
                .map(RoomEntity::toDomain)
                .toList();
    }

    @Override
    public List<Room> findReconcilable() {
        return jpaRepository
                .findAllByStartsAtIsNotNullAndStatusNotIn(
                        List.of(RoomStatus.ENDED, RoomStatus.CANCELLED))
                .stream()
                .map(RoomEntity::toDomain)
                .toList();
    }

    @Override
    public List<Room> findLegacyActive() {
        return jpaRepository.findAllByStatusAndStartsAtIsNull(RoomStatus.ACTIVE).stream()
                .map(RoomEntity::toDomain)
                .toList();
    }
}
