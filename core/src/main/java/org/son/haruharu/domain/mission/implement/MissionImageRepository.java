package org.son.haruharu.domain.mission.implement;
import java.util.Optional;
public interface MissionImageRepository { MissionImage save(MissionImage image); Optional<MissionImage> findById(Long id); java.util.List<MissionImage> findAllById(java.util.Collection<Long> ids); }
