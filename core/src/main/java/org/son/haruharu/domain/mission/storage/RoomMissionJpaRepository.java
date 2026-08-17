package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomMissionJpaRepository extends JpaRepository<RoomMissionEntity, Long> {

    boolean existsByRoomId(Long roomId);

    List<RoomMissionEntity> findByRoomIdOrderBySequenceAsc(Long roomId);
}
