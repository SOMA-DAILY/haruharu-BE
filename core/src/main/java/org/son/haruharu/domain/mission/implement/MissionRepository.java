package org.son.haruharu.domain.mission.implement;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MissionRepository {

    List<MissionCatalogEntry> findActiveCatalog();

    List<MissionRevision> findRevisionsByIds(Set<Long> revisionIds);

    Optional<MissionRevision> findRevisionById(Long revisionId);
}
