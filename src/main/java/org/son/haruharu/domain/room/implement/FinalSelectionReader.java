package org.son.haruharu.domain.room.implement;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class FinalSelectionReader {

    private final FinalSelectionRepository finalSelectionRepository;

    public FinalSelectionReader(FinalSelectionRepository finalSelectionRepository) {
        this.finalSelectionRepository = finalSelectionRepository;
    }

    public Optional<FinalSelection> findByRoomMemberId(UUID roomMemberId) {
        return finalSelectionRepository.findByRoomMemberId(roomMemberId);
    }

    public boolean hasSelected(UUID roomMemberId) {
        return finalSelectionRepository.existsByRoomMemberId(roomMemberId);
    }
}
