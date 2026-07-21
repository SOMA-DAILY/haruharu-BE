package org.son.haruharu.domain.room.storage;

import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RoomJpaRepository extends JpaRepository<RoomEntity, Long> {

    List<RoomEntity> findByIdInAndStatusNot(List<Long> ids, RoomStatus status);

    // 배치용 조회는 데모 방을 제외한다(#265) — 자동 시작·종료·위로구름·푸시가 데모 방을 안 건드리게.
    // 유저 본인 방 조회(findByIdInAndStatusNot)는 제외하지 않는다(데모 유저가 자기 데모 방을 봐야 함).
    List<RoomEntity> findAllByScheduledDateAndStatusAndDemoFalse(LocalDate scheduledDate, RoomStatus status);

    List<RoomEntity> findAllByStatusAndDemoFalse(RoomStatus status);
}
