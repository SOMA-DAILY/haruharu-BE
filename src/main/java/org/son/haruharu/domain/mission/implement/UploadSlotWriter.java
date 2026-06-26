package org.son.haruharu.domain.mission.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UploadSlotWriter {

    private final UploadSlotRepository uploadSlotRepository;

    public List<UploadSlot> saveAll(List<UploadSlot> slots) {
        return uploadSlotRepository.saveAll(slots);
    }
}
