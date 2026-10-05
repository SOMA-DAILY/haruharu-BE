package org.son.haruharu.domain.mission.storage;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMissionSettingJpaRepository extends JpaRepository<RoomMissionSettingEntity, Long> {

    Optional<RoomMissionSettingEntity> findByRoomId(Long roomId);
}
