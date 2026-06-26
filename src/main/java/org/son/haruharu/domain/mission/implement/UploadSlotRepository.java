package org.son.haruharu.domain.mission.implement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSlotRepository {

    List<UploadSlot> findAll();

    Optional<UploadSlot> findById(UUID id);

    List<UploadSlot> findByDayNumber(int dayNumber);

    List<UploadSlot> saveAll(List<UploadSlot> slots);
}
