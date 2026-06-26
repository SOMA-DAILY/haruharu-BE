package org.son.haruharu.domain.mission.implement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionRepository {

    List<Mission> findActive();

    Optional<Mission> findById(UUID id);

    List<Mission> saveAll(List<Mission> missions);
}
