package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UploadSlotReader {

    private final UploadSlotRepository uploadSlotRepository;

    /** 해당 Day의 슬롯(slot_index 순). currentDay=0(미시작)이면 빈 목록. */
    public List<UploadSlot> getByDay(int dayNumber) {
        return uploadSlotRepository.findByDayNumber(dayNumber).stream()
                .sorted((a, b) -> Integer.compare(a.slotIndex(), b.slotIndex()))
                .toList();
    }

    public List<UploadSlot> getAll() {
        return uploadSlotRepository.findAll();
    }
}
