package org.son.haruharu.domain.mission.storage;

import java.util.Optional;

import org.son.haruharu.domain.mission.implement.RoomMissionSetting;
import org.son.haruharu.domain.mission.implement.RoomMissionSettingRepository;
import org.springframework.stereotype.Repository;

@Repository
public class RoomMissionSettingRepositoryImpl implements RoomMissionSettingRepository {

    private final RoomMissionSettingJpaRepository jpaRepository;

    public RoomMissionSettingRepositoryImpl(RoomMissionSettingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RoomMissionSetting save(RoomMissionSetting setting) {
        return jpaRepository.save(RoomMissionSettingEntity.from(setting)).toDomain();
    }

    @Override
    public Optional<RoomMissionSetting> findByRoomId(Long roomId) {
        return jpaRepository.findByRoomId(roomId).map(RoomMissionSettingEntity::toDomain);
    }
}
