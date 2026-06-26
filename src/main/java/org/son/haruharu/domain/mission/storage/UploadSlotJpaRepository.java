package org.son.haruharu.domain.mission.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UploadSlotJpaRepository extends JpaRepository<UploadSlotEntity, UUID> {

    List<UploadSlotEntity> findByDayNumberOrderBySlotIndexAsc(int dayNumber);
}
