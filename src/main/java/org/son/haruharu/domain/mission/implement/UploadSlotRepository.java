package org.son.haruharu.domain.mission.implement;

import java.util.List;

public interface UploadSlotRepository {

    List<UploadSlot> findAll();

    List<UploadSlot> findByDayNumber(int dayNumber);

    List<UploadSlot> saveAll(List<UploadSlot> slots);
}
