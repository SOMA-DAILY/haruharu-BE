package org.son.haruharu.domain.room.implement;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class FinalSelectionReader {

    private final FinalSelectionRepository finalSelectionRepository;

    public FinalSelectionReader(FinalSelectionRepository finalSelectionRepository) {
        this.finalSelectionRepository = finalSelectionRepository;
    }

    public Optional<FinalSelection> findByRoomMemberId(Long roomMemberId) {
        return finalSelectionRepository.findByRoomMemberId(roomMemberId);
    }

    public boolean hasSelected(Long roomMemberId) {
        return finalSelectionRepository.existsByRoomMemberId(roomMemberId);
    }

    /** 방 멤버들의 최종선택 전체. 동시공개 resolve용. */
    public List<FinalSelection> findByRoomMemberIds(List<Long> roomMemberIds) {
        return finalSelectionRepository.findByRoomMemberIdIn(roomMemberIds);
    }
}
