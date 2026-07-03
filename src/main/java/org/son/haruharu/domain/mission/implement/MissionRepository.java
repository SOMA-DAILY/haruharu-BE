package org.son.haruharu.domain.mission.implement;

import java.util.List;
import java.util.Optional;

public interface MissionRepository {

    List<Mission> findActive();

    Optional<Mission> findById(Long id);

    List<Mission> saveAll(List<Mission> missions);
}
