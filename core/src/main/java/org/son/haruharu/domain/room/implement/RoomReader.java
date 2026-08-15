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
        // ENDED·CANCELLED(#311, sticky 종료 상태) 방은 '현재 방'에서 제외한다.
        return roomRepository.findByIdInAndStatusNot(candidateRoomIds, RoomStatus.ENDED).stream()
                .filter(room -> room.status() != RoomStatus.CANCELLED)
                .findFirst();
    }

    /** 09:00 자동 시작 배치용. 데모 방은 제외(#265) — 데모 방은 즉시 ACTIVE로 생성되고 배치와 무관하다. */
    public List<Room> getScheduledPending(LocalDate scheduledDate) {
        return roomRepository.findAllByScheduledDateAndStatusAndDemoFalse(scheduledDate, RoomStatus.PENDING);
    }

    /** 종료·위로구름·슬롯/최종선택 푸시 배치의 단일 진입점. 데모 방은 제외(#265) — 무만료·무진행 보장. */
    public List<Room> getActive() {
        return roomRepository.findAllByStatusAndDemoFalse(RoomStatus.ACTIVE);
    }
}
