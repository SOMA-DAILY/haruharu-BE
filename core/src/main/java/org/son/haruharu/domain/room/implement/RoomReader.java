package org.son.haruharu.domain.room.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RoomReader {

    private final RoomRepository roomRepository;

    public Optional<Room> findById(Long roomId) {
        return roomRepository.findById(roomId);
    }

    /** 후보 방 id들 중 종료되지 않은(PENDING/ACTIVE) 현재 방을 단일 조회로 찾는다. */
    public Optional<Room> findCurrent(List<Long> candidateRoomIds) {
        return roomRepository.findByIdInAndStatusNot(candidateRoomIds, RoomStatus.ENDED).stream()
                .findFirst();
    }

    /** 기준일 이전(포함) 시작 예정인 PENDING 방. 정확 일치가 아니라 <= 조회라서 놓친 활성화 실행을 회수할 수 있다(#170). */
    public List<Room> getPendingScheduledOnOrBefore(LocalDate date) {
        return roomRepository.findAllByScheduledDateLessThanEqualAndStatus(date, RoomStatus.PENDING);
    }

    public List<Room> getActive() {
        return roomRepository.findAllByStatus(RoomStatus.ACTIVE);
    }
}
