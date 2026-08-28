package org.son.haruharu.domain.mission.storage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMissionSlotJpaRepository extends JpaRepository<RoomMissionSlotEntity, Long> {

    List<RoomMissionSlotEntity> findByRoomIdOrderByOpensAtAsc(Long roomId);
}
