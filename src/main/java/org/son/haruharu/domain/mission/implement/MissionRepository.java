package org.son.haruharu.domain.mission.implement;

import java.util.List;

public interface MissionRepository {

    List<Mission> findActive();

    List<Mission> saveAll(List<Mission> missions);
}
