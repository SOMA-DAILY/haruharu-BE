package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.mission.storage.exception.UploadSlotNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UploadSlotReader {

    private final UploadSlotRepository uploadSlotRepository;

    public UploadSlot getById(UUID id) {
        return uploadSlotRepository.findById(id)
                .orElseThrow(UploadSlotNotFoundException::new);
    }

    /** 해당 Day의 슬롯(slot_index 순, DB 정렬). currentDay=0(미시작)이면 빈 목록. */
    public List<UploadSlot> getByDay(int dayNumber) {
        return uploadSlotRepository.findByDayNumber(dayNumber);
    }

    public List<UploadSlot> getAll() {
        return uploadSlotRepository.findAll();
    }
}
