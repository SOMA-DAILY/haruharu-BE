package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoomReader {

    private final RoomRepository roomRepository;

    public Optional<Room> findById(UUID roomId) {
        return roomRepository.findById(roomId);
    }

    /** 후보 방 id들 중 종료되지 않은(PENDING/ACTIVE) 현재 방을 단일 조회로 찾는다. */
    public Optional<Room> findCurrent(List<UUID> candidateRoomIds) {
        return roomRepository.findByIdInAndStatusNot(candidateRoomIds, RoomStatus.ENDED).stream()
                .findFirst();
    }

    public List<Room> getScheduledPending(LocalDate scheduledDate) {
        return roomRepository.findAllByScheduledDateAndStatus(scheduledDate, RoomStatus.PENDING);
    }
}
