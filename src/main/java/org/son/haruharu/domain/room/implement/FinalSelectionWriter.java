package org.son.haruharu.domain.room.implement;

import org.springframework.stereotype.Component;

@Component
public class FinalSelectionWriter {

    private final FinalSelectionRepository finalSelectionRepository;

    public FinalSelectionWriter(FinalSelectionRepository finalSelectionRepository) {
        this.finalSelectionRepository = finalSelectionRepository;
    }

    public FinalSelection save(FinalSelection finalSelection) {
        return finalSelectionRepository.save(finalSelection);
    }
}
